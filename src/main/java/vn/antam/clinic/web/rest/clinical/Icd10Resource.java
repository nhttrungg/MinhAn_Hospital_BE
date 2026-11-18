package vn.antam.clinic.web.rest.clinical;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.antam.clinic.service.clinical.Icd10Service;

@RestController
@RequestMapping("/api/v1/catalog/icd10")
public class Icd10Resource {

    private final Icd10Service service;

    public Icd10Resource(Icd10Service service) {
        this.service = service;
    }

    @GetMapping
    public List<Icd10Service.Code> search(@RequestParam(defaultValue = "") String q) {
        return service.search(q);
    }
}
