package vn.antam.clinic.service.impl.booking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.antam.clinic.service.booking.PublicClinicService;
import vn.antam.clinic.service.dto.booking.PublicClinicDtos.*;

@Service
public class PublicClinicServiceImpl implements PublicClinicService {

    private final JdbcTemplate jdbc;

    public PublicClinicServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(cacheNames = "clinicSettings", sync = true)
    public ClinicView clinic() {
        var contacts = jdbc.query(
            "select code,label,phone from clinic_contact_channels where clinic_id=1 order by sort_order,code",
            (rs, row) ->
                new ContactPhone(rs.getString("code"), rs.getString("label"), rs.getString("phone"))
        );
        return jdbc.queryForObject(
            "select clinic_name,address,phone,email,working_hours,website,exists(select 1 from mock_data_runs where dataset='outpatient-directory-v1' and completed_at is not null) demo_data from clinic_settings where id=1",
            (rs, row) ->
                new ClinicView(
                    rs.getString("clinic_name"),
                    rs.getString("address"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    rs.getString("working_hours"),
                    rs.getBoolean("demo_data"),
                    rs.getString("website"),
                    contacts
                )
        );
    }
}
