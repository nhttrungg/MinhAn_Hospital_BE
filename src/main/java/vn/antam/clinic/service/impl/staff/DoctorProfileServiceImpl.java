package vn.antam.clinic.service.impl.staff;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.antam.clinic.service.shared.WorkloadGuard;
import vn.antam.clinic.service.staff.DoctorProfileService;
import vn.antam.clinic.service.support.ClinicJdbc;
import vn.antam.clinic.service.support.Text;

@Service
public class DoctorProfileServiceImpl implements DoctorProfileService {

    private final JdbcTemplate jdbc;
    private final WorkloadGuard guard;

    public DoctorProfileServiceImpl(JdbcTemplate jdbc, WorkloadGuard guard) {
        this.jdbc = jdbc;
        this.guard = guard;
    }

    @Override
    public Profile get(long id) {
        List<Profile> rows = jdbc.query(
            "select specialty_id,bio,photo_url,gender from doctor_profiles where staff_id=?",
            (r, n) ->
                new Profile(
                    r.getLong(1),
                    r.getString(2),
                    r.getString(3),
                    jdbc.query(
                        "select service_id from doctor_services where doctor_staff_id=? order by service_id",
                        (s, i) -> s.getLong(1),
                        id
                    ),
                    r.getString(4)
                ),
            id
        );
        return rows.isEmpty() ? new Profile(null, null, null, List.of(), null) : rows.getFirst();
    }

    @Override
    public void save(
        long id,
        String role,
        Long specialty,
        String bio,
        String photoUrl,
        List<Long> serviceIds,
        boolean active,
        String gender
    ) {
        if (
            gender != null &&
            !gender.isBlank() &&
            !java.util.Set.of("MALE", "FEMALE", "OTHER").contains(gender)
        ) throw ClinicJdbc.bad("Giới tính không hợp lệ.");
        if (!active || !"DOCTOR".equals(role)) {
            guard.check("doctor", id);
            return;
        }
        if (
            specialty == null ||
            serviceIds == null ||
            serviceIds.isEmpty() ||
            serviceIds.size() > 100 ||
            serviceIds.stream().distinct().count() != serviceIds.size()
        ) throw ClinicJdbc.bad("Bác sĩ cần chuyên khoa và ít nhất một dịch vụ khám khác nhau.");
        if (
            jdbc.queryForObject(
                "select count(*) from specialties sp join departments d on d.id=sp.department_id where sp.id=? and sp.active=1 and d.active=1",
                Integer.class,
                specialty
            ) == 0
        ) throw ClinicJdbc.bad("Chọn chuyên khoa thuộc khoa đang hoạt động.");
        Profile before = get(id);
        if (
            before.specialtyId() != null &&
            (!specialty.equals(before.specialtyId()) ||
                !before.serviceIds().equals(serviceIds.stream().sorted().toList()))
        ) guard.check("doctor", id);
        for (Long service : serviceIds)
            if (
                service == null ||
                jdbc.queryForObject(
                    "select count(*) from services where id=? and active=1 and category='EXAMINATION'",
                    Integer.class,
                    service
                ) == 0
            ) throw ClinicJdbc.bad("Chọn dịch vụ khám đang hoạt động.");
        jdbc.update(
            "insert into doctor_profiles(staff_id,specialty_id,bio) values(?,?,?) on duplicate key update specialty_id=values(specialty_id),bio=values(bio)",
            id,
            specialty,
            bio
        );
        if (photoUrl != null) jdbc.update(
            "update doctor_profiles set photo_url=? where staff_id=?",
            Text.blankToNull(photoUrl),
            id
        );
        // Omitted fields preserve existing metadata; an explicit blank clears it.
        if (gender != null) jdbc.update(
            "update doctor_profiles set gender=? where staff_id=?",
            Text.blankToNull(gender),
            id
        );
        jdbc.update("delete from doctor_services where doctor_staff_id=?", id);
        for (Long service : serviceIds)
            jdbc.update(
                "insert into doctor_services(doctor_staff_id,service_id) values(?,?)",
                id,
                service
            );
    }
}
