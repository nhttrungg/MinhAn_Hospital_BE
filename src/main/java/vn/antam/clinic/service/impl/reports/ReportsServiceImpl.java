package vn.antam.clinic.service.impl.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.antam.clinic.service.dto.reports.ReportsDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.reports.ReportsService;
import vn.antam.clinic.service.support.ClinicJdbc;
import vn.antam.clinic.service.support.Money;

@Service
public class ReportsServiceImpl implements ReportsService {

    private final JdbcTemplate jdbc;

    public ReportsServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Report report(LocalDate from, LocalDate to, Long doctorId, Long serviceId) {
        ClinicJdbc.require("ADMIN");
        var dates = ClinicJdbc.dates(from, to);
        List<DoctorStats> doctors = jdbc.query(
            "select st.id,u.full_name,count(*) booked,sum(a.checked_in_at is not null) checked_in,sum(a.status='COMPLETED') completed,sum(a.status='CANCELLED') cancelled,sum(a.status='NO_SHOW') no_show, " +
                "(select avg(er.rating) from encounter_reviews er where er.doctor_staff_id=st.id and er.visible=1 and er.created_at>=? and er.created_at<?) rating_average, " +
                "(select count(*) from encounter_reviews er where er.doctor_staff_id=st.id and er.visible=1 and er.created_at>=? and er.created_at<?) rating_count " +
                "from appointments a join appointment_slots sl on sl.id=a.slot_id join staff st on st.id=sl.doctor_staff_id join users u on u.id=st.user_id " +
                "where sl.starts_at>=? and sl.starts_at<? and (? is null or st.id=?) and (? is null or sl.service_id=?) group by st.id,u.full_name order by u.full_name,st.id",
            (r, n) ->
                new DoctorStats(
                    r.getLong("id"),
                    r.getString("full_name"),
                    r.getLong("booked"),
                    r.getLong("checked_in"),
                    r.getLong("completed"),
                    r.getLong("cancelled"),
                    r.getLong("no_show"),
                    r.getObject("rating_average", Double.class),
                    r.getLong("rating_count")
                ),
            dates.from(),
            dates.to(),
            dates.from(),
            dates.to(),
            dates.from(),
            dates.to(),
            doctorId,
            doctorId,
            serviceId,
            serviceId
        );
        BigDecimal revenue = jdbc.queryForObject(
            "select coalesce(sum(r.amount),0) from receipts r join invoices i on i.id=r.invoice_id join appointments a on a.id=i.appointment_id join appointment_slots sl on sl.id=a.slot_id " +
                "where r.recorded_at>=? and r.recorded_at<? and (? is null or sl.doctor_staff_id=?) and (? is null or sl.service_id=?)",
            BigDecimal.class,
            dates.from(),
            dates.to(),
            doctorId,
            doctorId,
            serviceId,
            serviceId
        );
        BigDecimal refunded = jdbc.queryForObject(
            "select coalesce(sum(r.amount),0) from refunds r join invoices i on i.id=r.invoice_id join appointments a on a.id=i.appointment_id join appointment_slots sl on sl.id=a.slot_id " +
                "where r.recorded_at>=? and r.recorded_at<? and (? is null or sl.doctor_staff_id=?) and (? is null or sl.service_id=?)",
            BigDecimal.class,
            dates.from(),
            dates.to(),
            doctorId,
            doctorId,
            serviceId,
            serviceId
        );
        revenue = revenue.subtract(refunded);
        BigDecimal balance = jdbc.queryForObject(
            "select coalesce(sum((select coalesce(sum(l.amount),0) from invoice_lines l where l.invoice_id=i.id and l.status='ACTIVE')-(select coalesce(sum(r.amount),0) from receipts r where r.invoice_id=i.id)+(select coalesce(sum(r.amount),0) from refunds r where r.invoice_id=i.id)),0) " +
                "from invoices i join appointments a on a.id=i.appointment_id join appointment_slots sl on sl.id=a.slot_id where i.status<>'VOID' and sl.starts_at>=? and sl.starts_at<? and (? is null or sl.doctor_staff_id=?) and (? is null or sl.service_id=?)",
            BigDecimal.class,
            dates.from(),
            dates.to(),
            doctorId,
            doctorId,
            serviceId,
            serviceId
        );
        return new Report(
            doctors.stream().mapToLong(DoctorStats::booked).sum(),
            doctors.stream().mapToLong(DoctorStats::checkedIn).sum(),
            doctors.stream().mapToLong(DoctorStats::completed).sum(),
            doctors.stream().mapToLong(DoctorStats::cancelled).sum(),
            doctors.stream().mapToLong(DoctorStats::noShow).sum(),
            Money.format(revenue),
            Money.format(balance),
            doctors
        );
    }

    @Override
    public String csv(LocalDate from, LocalDate to, Long doctorId, Long serviceId) {
        Report data = report(from, to, doctorId, serviceId);
        StringBuilder csv = new StringBuilder(
            "\uFEFFChỉ tiêu,Giá trị\r\nLịch đặt," +
                data.booked() +
                "\r\nTiếp nhận," +
                data.checkedIn() +
                "\r\nHoàn tất," +
                data.completed() +
                "\r\nHủy," +
                data.cancelled() +
                "\r\nVắng," +
                data.noShow() +
                "\r\nThực thu," +
                data.revenue() +
                "\r\nCông nợ," +
                data.balance() +
                "\r\n\r\nBác sĩ,Lịch đặt,Tiếp nhận,Hoàn tất,Hủy,Vắng,Điểm đánh giá,Số đánh giá\r\n"
        );
        for (DoctorStats d : data.doctors())
            csv.append(cell(d.doctorName()))
                .append(',')
                .append(d.booked())
                .append(',')
                .append(d.checkedIn())
                .append(',')
                .append(d.completed())
                .append(',')
                .append(d.cancelled())
                .append(',')
                .append(d.noShow())
                .append(',')
                .append(d.ratingAverage() == null ? "" : d.ratingAverage())
                .append(',')
                .append(d.ratingCount())
                .append("\r\n");
        return csv.toString();
    }

    private static String cell(String text) {
        if (text.matches("^[=+@\\-\\t\\r].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    @Override
    public PageResponse<Audit> audit(String q, int page, int size) {
        ClinicJdbc.require("ADMIN");
        String like = ClinicJdbc.like(q);
        return ClinicJdbc.queryPage(
            jdbc,
            "select a.*,u.full_name actor_name from audit_logs a left join users u on u.id=a.actor_user_id where a.action like ? or a.entity_type like ? or a.entity_id like ? or u.full_name like ? order by a.id desc",
            (r, n) ->
                new Audit(
                    r.getLong("id"),
                    r.getString("actor_name"),
                    r.getString("action"),
                    r.getString("entity_type"),
                    r.getString("entity_id"),
                    r.getString("detail"),
                    r.getString("request_id"),
                    r.getTimestamp("created_at").toInstant().toString()
                ),
            page,
            size,
            like,
            like,
            like,
            like
        );
    }
}
