package vn.antam.clinic.web.rest.clinical;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.antam.clinic.service.clinical.ReviewService;
import vn.antam.clinic.service.dto.clinical.ReviewDtos.*;
import vn.antam.clinic.service.dto.shared.PageResponse;

@RestController
@RequestMapping("/api/v1")
public class ReviewResource {

    private final ReviewService reviews;

    public ReviewResource(ReviewService reviews) {
        this.reviews = reviews;
    }

    @PostMapping("/encounters/{id}/review")
    public ReviewView create(@PathVariable long id, @Valid @RequestBody ReviewRequest body) {
        return reviews.create(id, body);
    }

    @GetMapping("/encounters/{id}/review")
    public ReviewView mine(@PathVariable long id) {
        return reviews.mine(id);
    }

    @GetMapping("/doctors/{id}/reviews")
    public PageResponse<ReviewView> publicReviews(
        @PathVariable long id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "5") int size
    ) {
        return reviews.forDoctor(id, page, size);
    }

    @GetMapping("/reviews/search")
    public PageResponse<ReviewView> search(
        @RequestParam(required = false) Long doctorId,
        @RequestParam(required = false) Boolean visible,
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return reviews.search(doctorId, visible, q, page, size);
    }

    @PostMapping("/reviews/{id}/visibility")
    public ReviewView visibility(
        @PathVariable long id,
        @Valid @RequestBody VisibilityRequest body
    ) {
        return reviews.visibility(id, body);
    }
}
