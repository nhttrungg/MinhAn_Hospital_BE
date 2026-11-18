package vn.antam.clinic.service.impl.demo;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.demo.ClinicalDemoCatalogSeeder;
import vn.antam.clinic.service.demo.UpgradeFeaturesSeeder;

/** Opt-in, fictional fixtures. The marker and all writes commit together. */
@Service
public class UpgradeFeaturesSeederImpl implements UpgradeFeaturesSeeder {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ClinicalDemoCatalogSeeder catalog;

    public UpgradeFeaturesSeederImpl(
        JdbcTemplate jdbc,
        Clock clock,
        ClinicalDemoCatalogSeeder catalog
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.catalog = catalog;
    }

    @Override
    @Transactional
    public boolean initialize() {
        LocalDate today = LocalDate.now(clock);
        jdbc.update(
            "insert into mock_data_runs(dataset,initialized_on) values(?,?) on duplicate key update dataset=dataset",
            DATASET,
            today
        );
        if (
            Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "select completed_at is not null from mock_data_runs where dataset=? for update",
                    Boolean.class,
                    DATASET
                )
            )
        ) return false;
        catalog.initialize();
        jdbc.update(
            "insert into patient_profiles(patient_code,owner_user_id,full_name,date_of_birth,gender,relationship) " +
                "select 'UP-FAMILY-01',u.id,'Bé An (demo)',?,'FEMALE','CHILD' from users u where u.username='benhnhan01' " +
                "and not exists(select 1 from patient_profiles where patient_code='UP-FAMILY-01')",
            today.minusYears(8)
        );
        jdbc.update(
            "insert into patient_allergies(patient_id,ingredient,reaction,severity) " +
                "select p.id,'Amoxicillin','Nổi mẩn (minh họa, không dùng điều trị)','MAJOR' from patient_profiles p join users u on u.id=p.user_id " +
                "where u.username='benhnhan01' and not exists(select 1 from patient_allergies pa where pa.patient_id=p.id and pa.ingredient='Amoxicillin')"
        );
        jdbc.update(
            "insert into encounter_reviews(encounter_id,patient_id,doctor_staff_id,rating,comment,created_at) " +
                "select e.id,e.patient_id,e.doctor_staff_id,5,'Đánh giá minh họa: được hướng dẫn rõ ràng.',? from encounters e join appointments a on a.id=e.appointment_id " +
                "where a.appointment_code like 'MOCK-LH%' and e.status='COMPLETED' and not exists(select 1 from encounter_reviews er where er.encounter_id=e.id) order by e.id limit 3",
            Timestamp.from(clock.instant())
        );
        jdbc.update(
            "update encounters e join appointments a on a.id=e.appointment_id set e.follow_up_date=? " +
                "where a.appointment_code like 'MOCK-LH%' and e.status='COMPLETED' and e.follow_up_date is null",
            today.plusDays(3)
        );
        var priorBatches = jdbc.queryForList(
            "select id from medication_batches where batch_code='UP-NEAR-EXPIRY'",
            Long.class
        );
        jdbc.update(
            "insert into medication_batches(counter_id,medication_id,batch_code,expiry_date,quantity,reserved) " +
                "select c.id,m.id,'UP-NEAR-EXPIRY',?,10,0 from pharmacy_counters c cross join medications m " +
                "where c.active=1 and m.code='MOCK-TH001' and not exists(select 1 from medication_batches b where b.counter_id=c.id and b.medication_id=m.id and b.batch_code='UP-NEAR-EXPIRY')",
            today.plusDays(15)
        );
        // A seeded lot must have the same opening ledger as a lot received through the pharmacy API.
        for (long batch : jdbc.queryForList(
            "select id from medication_batches where batch_code='UP-NEAR-EXPIRY'",
            Long.class
        )) {
            if (priorBatches.contains(batch)) continue;
            jdbc.update(
                "insert into stock_movements(batch_id,action,quantity_delta,reserved_delta,quantity_after,reserved_after,actor_user_id,reason,occurred_at) select ?,'RECEIVE',10,0,10,0,id,'Nhập lô gần hết hạn minh họa',? from users where username='admin'",
                batch,
                Timestamp.from(clock.instant())
            );
        }
        jdbc.update(
            "update mock_data_runs set completed_at=? where dataset=?",
            Timestamp.from(clock.instant()),
            DATASET
        );
        return true;
    }
}
