package vn.antam.clinic.service.impl.identity;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.antam.clinic.service.dto.identity.EmailTaskDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.identity.EmailTaskService;
import vn.antam.clinic.service.support.ClinicJdbc;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
public class EmailTaskServiceImpl implements EmailTaskService {

    private final JdbcTemplate jdbc;

    public EmailTaskServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EmailTaskView> list() {
        return jdbc.query(
            "select id,recipient,subject,status,attempts,last_error from email_outbox " +
                "order by id desc limit 100",
            (rs, row) ->
                new EmailTaskView(
                    rs.getLong(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getInt(5),
                    rs.getString(6)
                )
        );
    }

    @Override
    public PageResponse<EmailTaskView> search(String q, String status, int page, int size) {
        String like = ClinicJdbc.like(q);
        return ClinicJdbc.queryPage(
            jdbc,
            "select id,recipient,subject,status,attempts,last_error from email_outbox where (? is null or status=?) and (recipient like ? or subject like ?) order by id desc",
            (rs, row) ->
                new EmailTaskView(
                    rs.getLong(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getInt(5),
                    rs.getString(6)
                ),
            page,
            size,
            status,
            status,
            like,
            like
        );
    }

    @Override
    public EmailTaskView retry(long id) {
        int count = jdbc.update(
            "update email_outbox set status='PENDING',attempts=0,next_attempt_at=UTC_TIMESTAMP(6)," +
                "last_error=null where id=? and status='FAILED'",
            id
        );
        if (count != 1) throw new ApiException(
            HttpStatus.CONFLICT,
            "Không thể gửi lại",
            "Chỉ gửi lại email ở trạng thái lỗi."
        );
        return jdbc.queryForObject(
            "select id,recipient,subject,status,attempts,last_error from email_outbox where id=?",
            (rs, row) ->
                new EmailTaskView(
                    rs.getLong(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getInt(5),
                    rs.getString(6)
                ),
            id
        );
    }
}
