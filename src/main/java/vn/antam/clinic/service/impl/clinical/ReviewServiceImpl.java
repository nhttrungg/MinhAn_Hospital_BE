package vn.antam.clinic.service.impl.clinical;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.security.PatientAccess;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.clinical.ReviewService;
import vn.antam.clinic.service.dto.clinical.ReviewDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.notification.AppointmentNotificationService;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final JdbcTemplate jdbc;
    private final PatientAccess access;
    private final Clock clock;
    private final AuditService audit;
    private final AppointmentNotificationService notices;

    public ReviewServiceImpl(
        JdbcTemplate jdbc,
        PatientAccess access,
        Clock clock,
        AuditService audit,
        AppointmentNotificationService notices
    ) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
        this.audit = audit;
        this.notices = notices;
    }

    @Override
    @Transactional
    public ReviewView create(long encounterId, ReviewRequest request) {
        require("PATIENT");
        var visits = jdbc.queryForList(
            "select * from encounters where id=? for update",
            encounterId
        );
        if (visits.isEmpty()) throw missing();
        var visit = visits.getFirst();
        access.requireOwn(user(), ((Number) visit.get("patient_id")).longValue());
        if (
            request.rating() == null ||
            request.rating() < 1 ||
            request.rating() > 5 ||
            (request.comment() != null && request.comment().length() > 1000)
        ) throw bad("Điểm đánh giá từ 1 đến 5; nhận xét tối đa 1000 ký tự.");
        var completed = instant(visit.get("completed_at"));
        if (
            !"COMPLETED".equals(visit.get("status")) ||
            completed == null ||
            completed.isAfter(clock.instant()) ||
            completed.plus(Duration.ofDays(30)).isBefore(clock.instant())
        ) throw conflict("Chỉ đánh giá lượt khám đã hoàn tất trong vòng 30 ngày.");
        if (
            jdbc.queryForObject(
                "select count(*) from encounter_reviews where encounter_id=?",
                Integer.class,
                encounterId
            ) > 0
        ) throw conflict("Lượt khám đã được đánh giá.");
        long id = insert(
            jdbc,
            "insert into encounter_reviews(encounter_id,patient_id,doctor_staff_id,rating,comment) values(?,?,?,?,?)",
            encounterId,
            visit.get("patient_id"),
            visit.get("doctor_staff_id"),
            request.rating(),
            request.comment() == null ? null : request.comment().trim()
        );
        audit.record("CREATE", "ENCOUNTER_REVIEW", String.valueOf(id), null);
        return find(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewView mine(long encounterId) {
        require("PATIENT");
        var ids = jdbc.queryForList("select patient_id from encounters where id=?", encounterId);
        if (ids.isEmpty()) throw missing();
        access.requireOwn(user(), ((Number) ids.getFirst().get("patient_id")).longValue());
        var reviews = jdbc.query(
            select() + " where r.encounter_id=?",
            (r, n) -> map(r, false),
            encounterId
        );
        return reviews.isEmpty() ? null : reviews.getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewView> forDoctor(long doctorId, int page, int size) {
        return queryPage(
            jdbc,
            select() +
                " join staff st on st.id=r.doctor_staff_id join users du on du.id=st.user_id where r.doctor_staff_id=? and r.visible=1 and st.active=1 and du.status='ACTIVE' order by r.created_at desc,r.id desc",
            (r, n) -> map(r, true),
            page,
            size,
            doctorId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewView> search(
        Long doctor,
        Boolean visible,
        String query,
        int page,
        int size
    ) {
        require("ADMIN");
        String q = like(query);
        return queryPage(
            jdbc,
            select() +
                " where (? is null or r.doctor_staff_id=?) and (? is null or r.visible=?) and (p.full_name like ? or r.comment like ?) order by r.created_at desc,r.id desc",
            (r, n) -> map(r, false),
            page,
            size,
            doctor,
            doctor,
            visible,
            visible,
            q,
            q
        );
    }

    @Override
    @Transactional
    public ReviewView visibility(long id, VisibilityRequest request) {
        require("ADMIN");
        if (
            request.visible() == null ||
            request.reasonCode() == null ||
            request.reason() == null ||
            request.reason().isBlank() ||
            request.reason().length() > 500
        ) {
            throw bad("Chọn loại kiểm duyệt và nhập lý do từ 1 đến 500 ký tự.");
        }
        boolean visible = request.visible();
        if (visible != (request.reasonCode() == ModerationReason.RESTORE)) {
            throw bad(
                "Chỉ dùng lý do khôi phục khi công khai; chọn loại vi phạm hợp lệ khi ẩn đánh giá."
            );
        }
        var rows = jdbc.queryForList(
            "select visible from encounter_reviews where id=? for update",
            id
        );
        if (rows.isEmpty()) throw missing();
        Object old = rows.getFirst().get("visible");
        boolean previous = old instanceof Number n ? n.intValue() != 0 : Boolean.TRUE.equals(old);
        if (previous == visible) return find(id);
        jdbc.update("update encounter_reviews set visible=? where id=?", visible, id);
        audit.record(
            visible ? "SHOW" : "HIDE",
            "ENCOUNTER_REVIEW",
            String.valueOf(id),
            "visible=" +
                previous +
                " -> " +
                visible +
                "; reasonCode=" +
                request.reasonCode() +
                "; reason=" +
                request.reason().trim()
        );
        return find(id);
    }

    /** Integrate with EncounterService.complete inside its transaction. */
    @Override
    @Transactional
    public void invite(long encounterId) {
        var visits = jdbc.queryForList(
            "select e.patient_id from encounters e join patient_profiles p on p.id=e.patient_id where e.id=? and e.status='COMPLETED' and p.active=1",
            encounterId
        );
        if (visits.isEmpty()) return;
        Long recipient = access.recipientUserId(
            ((Number) visits.getFirst().get("patient_id")).longValue()
        );
        if (recipient != null) notices.notify(
            recipient,
            "REVIEW",
            "Đánh giá lượt khám",
            "Bạn có thể chia sẻ trải nghiệm sau lượt khám vừa hoàn tất trong vòng 30 ngày.",
            "/portal/records/" + encounterId,
            "review-invite:" + encounterId
        );
    }

    private ReviewView find(long id) {
        return jdbc.queryForObject(select() + " where r.id=?", (r, n) -> map(r, false), id);
    }

    private static String select() {
        return "select r.*,p.full_name patient_name from encounter_reviews r join patient_profiles p on p.id=r.patient_id";
    }

    private static ReviewView map(ResultSet r, boolean publicView) throws SQLException {
        return new ReviewView(
            r.getLong("id"),
            publicView ? null : r.getLong("encounter_id"),
            publicView ? null : r.getLong("patient_id"),
            r.getLong("doctor_staff_id"),
            r.getInt("rating"),
            r.getString("comment"),
            publicView ? mask(r.getString("patient_name")) : r.getString("patient_name"),
            r.getBoolean("visible"),
            r.getTimestamp("created_at").toInstant()
        );
    }

    public static String mask(String name) {
        if (name == null || name.isBlank()) return "Bệnh nhân";
        String[] words = name.trim().split("\\s+");
        if (words.length == 1) return words[0].substring(0, 1) + "***";
        StringBuilder result = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) result
            .append(" ")
            .appendCodePoint(words[i].codePointAt(0))
            .append(".");
        return result.toString();
    }
}
