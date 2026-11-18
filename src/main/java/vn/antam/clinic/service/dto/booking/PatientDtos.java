package vn.antam.clinic.service.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class PatientDtos {

    private PatientDtos() {}

    public record PatientView(
        long id,
        String code,
        String fullName,
        String phone,
        String email,
        LocalDate dateOfBirth,
        String gender,
        String address,
        String allergies,
        String medicalHistory,
        boolean active
    ) {}

    public record PatientInput(
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 20) String phone,
        @Size(max = 255) String email,
        LocalDate dateOfBirth,
        @Size(max = 20) String gender,
        @Size(max = 500) String address,
        @Size(max = 5000) String allergies,
        @Size(max = 5000) String medicalHistory
    ) {}
}
