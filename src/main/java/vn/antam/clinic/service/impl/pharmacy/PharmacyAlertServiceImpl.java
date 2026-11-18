package vn.antam.clinic.service.impl.pharmacy;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.pharmacy.PharmacyAlertService;
import vn.antam.clinic.service.support.ClinicJdbc;
import vn.antam.clinic.service.support.WorkflowSupport;

@Service
public class PharmacyAlertServiceImpl implements PharmacyAlertService {

    private final WorkflowSupport w;
    private final Clock clock;

    public PharmacyAlertServiceImpl(WorkflowSupport w, Clock clock) {
        this.w = w;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Alerts alerts(Long counterId) {
        ClinicJdbc.require("ADMIN", "PHARMACIST");
        var counters =
            counterId == null
                ? w
                      .db()
                      .query(
                          "select c.id from pharmacy_counters c where c.active=1 and (?=1 or exists(select 1 from counter_staff cs join staff s on s.id=cs.staff_id where cs.counter_id=c.id and s.active=1 and s.user_id=?)) order by c.id",
                          (r, n) -> r.getLong(1),
                          ClinicJdbc.role("ADMIN") ? 1 : 0,
                          ClinicJdbc.user()
                      )
                : List.of(counterId);
        for (long counter : counters) w.counterRead(counter);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        Integer days = w
            .db()
            .queryForObject(
                "select near_expiry_days from clinic_settings where id=1",
                Integer.class
            );
        int threshold = days == null ? 60 : days;
        var expired = new ArrayList<Batch>();
        var near = new ArrayList<Batch>();
        var low = new ArrayList<LowStock>();
        for (long counter : counters) {
            var batches = w
                .db()
                .query(
                    "select b.id,b.counter_id,b.medication_id,m.name,m.unit,b.batch_code,b.expiry_date,b.quantity,b.quantity-b.reserved available from medication_batches b join medications m on m.id=b.medication_id where b.counter_id=? and b.quantity>0 and b.expiry_date<=? order by b.expiry_date,b.id",
                    (r, n) ->
                        new Batch(
                            r.getLong(1),
                            r.getLong(2),
                            r.getLong(3),
                            r.getString(4),
                            r.getString(5),
                            r.getString(6),
                            r.getObject(7, LocalDate.class),
                            r.getBigDecimal(8),
                            r.getBigDecimal(9)
                        ),
                    counter,
                    today.plusDays(threshold)
                );
            for (var b : batches) {
                if (!b.expiryDate().isAfter(today)) expired.add(b);
                else near.add(b);
            }
            low.addAll(
                w
                    .db()
                    .query(
                        "select m.id,m.name,m.unit,m.reorder_level,coalesce(sum(case when b.active=1 and b.expiry_date>? then b.quantity-b.reserved else 0 end),0) available from medications m left join medication_batches b on b.medication_id=m.id and b.counter_id=? where m.active=1 and m.reorder_level>0 group by m.id,m.name,m.unit,m.reorder_level having available<m.reorder_level order by m.name,m.id",
                        (r, n) ->
                            new LowStock(
                                r.getLong("id"),
                                counter,
                                r.getString("name"),
                                r.getString("unit"),
                                r.getBigDecimal("available"),
                                r.getInt("reorder_level")
                            ),
                        today,
                        counter
                    )
            );
        }
        return new Alerts(
            List.copyOf(expired),
            List.copyOf(near),
            List.copyOf(low),
            expired.size() + near.size() + low.size(),
            today,
            threshold
        );
    }
}
