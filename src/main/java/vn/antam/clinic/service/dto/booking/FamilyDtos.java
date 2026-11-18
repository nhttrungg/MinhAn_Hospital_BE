package vn.antam.clinic.service.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import vn.antam.clinic.service.dto.booking.PatientDtos.PatientInput;
import vn.antam.clinic.service.dto.booking.PatientDtos.PatientView;

public final class FamilyDtos {

    private FamilyDtos() {}

    public record FamilyInput(
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 20) String phone,
        @Size(max = 255) String email,
        LocalDate dateOfBirth,
        @Size(max = 20) String gender,
        @Size(max = 500) String address,
        @Size(max = 5000) String allergies,
        @Size(max = 5000) String medicalHistory,
        @NotBlank @Size(max = 30) String relationship
    ) {
        public PatientInput patient() {
            return new PatientInput(
                fullName,
                phone,
                email,
                dateOfBirth,
                gender,
                address,
                allergies,
                medicalHistory
            );
        }
    }

    public record FamilyView(
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
        boolean active,
        String relationship
    ) {
        public static FamilyView from(PatientView p, String relationship) {
            return new FamilyView(
                p.id(),
                p.code(),
                p.fullName(),
                p.phone(),
                p.email(),
                p.dateOfBirth(),
                p.gender(),
                p.address(),
                p.allergies(),
                p.medicalHistory(),
                p.active(),
                relationship
            );
        }
    }
}
