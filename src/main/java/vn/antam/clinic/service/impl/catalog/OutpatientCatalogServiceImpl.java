package vn.antam.clinic.service.impl.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.catalog.OutpatientCatalogService;
import vn.antam.clinic.service.dto.catalog.OutpatientCatalogDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.shared.WorkloadGuard;
import vn.antam.clinic.service.support.ClinicJdbc;

@Service
public class OutpatientCatalogServiceImpl implements OutpatientCatalogService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final WorkloadGuard guard;

    public OutpatientCatalogServiceImpl(
        JdbcTemplate jdbc,
        AuditService audit,
        WorkloadGuard guard
    ) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.guard = guard;
    }

    @Override
    public PageResponse<Specialty> specialties(String q, Boolean active, int page, int size) {
        ClinicJdbc.require("ADMIN", "DOCTOR");
        String like = ClinicJdbc.like(q);
        return ClinicJdbc.queryPage(
            jdbc,
            "select * from specialties where (? is null or active=?) and (code like ? or name like ?) order by name,id",
            (r, n) ->
                new Specialty(
                    r.getLong("id"),
                    r.getLong("department_id"),
                    r.getString("code"),
                    r.getString("name"),
                    r.getString("description"),
                    r.getBoolean("active")
                ),
            page,
            size,
            active,
            active,
            like,
            like
        );
    }

    @Override
    @Transactional
    public Specialty createSpecialty(SpecialtyInput in) {
        return saveSpecialty(null, in);
    }

    @Override
    @Transactional
    public Specialty updateSpecialty(long id, SpecialtyInput in) {
        return saveSpecialty(id, in);
    }

    private Specialty saveSpecialty(Long id, SpecialtyInput in) {
        ClinicJdbc.require("ADMIN");
        if (
            in.active() &&
            jdbc.queryForObject(
                "select count(*) from departments where id=? and active=1",
                Integer.class,
                in.departmentId()
            ) == 0
        ) throw ClinicJdbc.bad("Chọn khoa đang hoạt động.");
        if (id != null) {
            if (!in.active()) guard.check("specialty", id);
            if (
                jdbc.update(
                    "update specialties set department_id=?,code=?,name=?,description=?,active=? where id=?",
                    in.departmentId(),
                    in.code().toUpperCase(),
                    in.name().trim(),
                    in.description(),
                    in.active(),
                    id
                ) != 1
            ) throw ClinicJdbc.missing();
        } else id = ClinicJdbc.insert(
            jdbc,
            "insert into specialties(department_id,code,name,description,active) values(?,?,?,?,?)",
            in.departmentId(),
            in.code().toUpperCase(),
            in.name().trim(),
            in.description(),
            in.active()
        );
        audit.record("SAVE", "SPECIALTY", id.toString(), null);
        return new Specialty(
            id,
            in.departmentId(),
            in.code().toUpperCase(),
            in.name().trim(),
            in.description(),
            in.active()
        );
    }

    @Override
    public PageResponse<Medication> medications(String q, Boolean active, int page, int size) {
        ClinicJdbc.require("ADMIN", "DOCTOR");
        String like = ClinicJdbc.like(q);
        return ClinicJdbc.queryPage(
            jdbc,
            "select * from medications where (? is null or active=?) and (code like ? or name like ? or ingredient like ?) order by name,id",
            (r, n) ->
                new Medication(
                    r.getLong("id"),
                    r.getString("code"),
                    r.getString("name"),
                    r.getString("ingredient"),
                    r.getString("strength"),
                    r.getString("form"),
                    r.getString("unit"),
                    r.getBoolean("active"),
                    r.getInt("reorder_level")
                ),
            page,
            size,
            active,
            active,
            like,
            like,
            like
        );
    }

    @Override
    @Transactional
    public Medication createMedication(MedicationInput in) {
        return saveMedication(null, in);
    }

    @Override
    @Transactional
    public Medication updateMedication(long id, MedicationInput in) {
        return saveMedication(id, in);
    }

    private Medication saveMedication(Long id, MedicationInput in) {
        ClinicJdbc.require("ADMIN");
        if (id == null) id = ClinicJdbc.insert(
            jdbc,
            "insert into medications(code,name,ingredient,strength,form,unit,active,reorder_level) values(?,?,?,?,?,?,?,coalesce(?,0))",
            in.code().toUpperCase(),
            in.name().trim(),
            in.ingredient(),
            in.strength(),
            in.form(),
            in.unit(),
            in.active(),
            in.reorderLevel()
        );
        else if (
            jdbc.update(
                "update medications set code=?,name=?,ingredient=?,strength=?,form=?,unit=?,active=?,reorder_level=coalesce(?,reorder_level) where id=?",
                in.code().toUpperCase(),
                in.name().trim(),
                in.ingredient(),
                in.strength(),
                in.form(),
                in.unit(),
                in.active(),
                in.reorderLevel(),
                id
            ) != 1
        ) throw ClinicJdbc.missing();
        audit.record("SAVE", "MEDICATION", id.toString(), null);
        int reorderLevel = jdbc.queryForObject(
            "select reorder_level from medications where id=?",
            Integer.class,
            id
        );
        return new Medication(
            id,
            in.code().toUpperCase(),
            in.name().trim(),
            in.ingredient(),
            in.strength(),
            in.form(),
            in.unit(),
            in.active(),
            reorderLevel
        );
    }

    @Override
    public Settings settings() {
        ClinicJdbc.require("ADMIN");
        return jdbc.queryForObject("select * from clinic_settings where id=1", (r, n) ->
            new Settings(
                r.getString("clinic_name"),
                r.getString("address"),
                r.getString("phone"),
                r.getString("email"),
                r.getString("working_hours"),
                r.getBoolean("queue_display_enabled"),
                r.getBoolean("queue_display_mask_names"),
                r.getBoolean("reminders_enabled"),
                r.getInt("reminder_hours_before"),
                r.getInt("near_expiry_days")
            )
        );
    }

    @Override
    @Transactional
    public Settings updateSettings(Settings in) {
        ClinicJdbc.require("ADMIN");
        jdbc.update(
            "update clinic_settings set clinic_name=?,address=?,phone=?,email=?,working_hours=?, " +
                "queue_display_enabled=coalesce(?,queue_display_enabled),queue_display_mask_names=coalesce(?,queue_display_mask_names), " +
                "reminders_enabled=coalesce(?,reminders_enabled),reminder_hours_before=coalesce(?,reminder_hours_before), " +
                "near_expiry_days=coalesce(?,near_expiry_days) where id=1",
            in.name().trim(),
            in.address(),
            in.phone(),
            in.email(),
            in.workingHours(),
            in.queueDisplayEnabled(),
            in.queueDisplayMaskNames(),
            in.remindersEnabled(),
            in.reminderHoursBefore(),
            in.nearExpiryDays()
        );
        audit.record("UPDATE", "CLINIC_SETTINGS", "1", null);
        return settings();
    }
}
