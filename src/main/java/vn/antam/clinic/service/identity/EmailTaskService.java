package vn.antam.clinic.service.identity;

import java.util.List;
import vn.antam.clinic.service.dto.identity.EmailTaskDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;

/** Business contract; implemented by the Spring-managed EmailTaskServiceImpl. */
public interface EmailTaskService {
    public List<EmailTaskView> list();

    public PageResponse<EmailTaskView> search(String q, String status, int page, int size);

    public EmailTaskView retry(long id);
}
