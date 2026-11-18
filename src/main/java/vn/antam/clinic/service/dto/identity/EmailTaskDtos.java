package vn.antam.clinic.service.dto.identity;

public final class EmailTaskDtos {

    private EmailTaskDtos() {}

    public record EmailTaskView(
        long id,
        String recipient,
        String subject,
        String status,
        int attempts,
        String lastError
    ) {}
}
