package vn.antam.clinic.service.clinical;

import vn.antam.clinic.service.dto.clinical.ReviewDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;

/** Business contract; implemented by the Spring-managed ReviewServiceImpl. */
public interface ReviewService {
    public ReviewView create(long encounterId, ReviewRequest request);

    public ReviewView mine(long encounterId);

    public PageResponse<ReviewView> forDoctor(long doctorId, int page, int size);

    public PageResponse<ReviewView> search(
        Long doctor,
        Boolean visible,
        String query,
        int page,
        int size
    );

    public ReviewView visibility(long id, VisibilityRequest request);

    public void invite(long encounterId);
}
