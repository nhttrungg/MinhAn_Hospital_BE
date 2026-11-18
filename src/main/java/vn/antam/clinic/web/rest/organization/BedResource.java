package vn.antam.clinic.web.rest.organization;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.domain.organization.enumeration.BedStatus;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.BedResponse;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.CreateBedRequest;
import vn.antam.clinic.service.dto.organization.OrganizationDtos.UpdateBedRequest;
import vn.antam.clinic.service.dto.shared.PageResponse;
import vn.antam.clinic.service.organization.BedService;

@RestController
@RequestMapping("/api/v1/catalog/beds")
public class BedResource {

    private final BedService beds;

    public BedResource(BedService beds) {
        this.beds = beds;
    }

    @GetMapping
    public PageResponse<BedResponse> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Long roomId,
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) BedStatus status,
        @RequestParam(required = false) Boolean active,
        @PageableDefault(size = 20, sort = "code") Pageable pageable
    ) {
        return beds.search(q, roomId, departmentId, status, active, pageable);
    }

    @GetMapping("/{id}")
    public BedResponse get(@PathVariable long id) {
        return beds.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BedResponse create(@Valid @RequestBody CreateBedRequest request) {
        return beds.create(request);
    }

    @PutMapping("/{id}")
    public BedResponse update(@PathVariable long id, @Valid @RequestBody UpdateBedRequest request) {
        return beds.update(id, request);
    }
}
