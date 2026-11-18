package vn.antam.clinic.service.dto.booking;

import java.util.List;

public final class PublicClinicDtos {

    private PublicClinicDtos() {}

    public record ClinicView(
        String name,
        String address,
        String phone,
        String email,
        String workingHours,
        boolean demoData,
        String website,
        List<ContactPhone> contactPhones
    ) {}

    public record ContactPhone(String code, String label, String phone) {}
}
