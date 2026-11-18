package vn.antam.clinic.web.rest.booking;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.booking.PublicClinicService;
import vn.antam.clinic.service.dto.booking.PublicClinicDtos.*;

@RestController
public class PublicClinicResource {

    private final PublicClinicService service;

    public PublicClinicResource(PublicClinicService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/public/clinic")
    public ClinicView clinic() {
        return service.clinic();
    }
}
