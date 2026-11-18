package vn.antam.clinic.service.dto.billing;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class BillingDtos {

    private BillingDtos() {}

    public record LineView(
        long id,
        String sourceType,
        String description,
        String unit,
        String quantity,
        String unitPrice,
        String amount,
        String status
    ) {}

    public record ReceiptView(
        long id,
        String code,
        long invoiceId,
        String amount,
        String method,
        String recordedAt
    ) {}

    public record InvoiceView(
        long id,
        String code,
        long patientId,
        String patientName,
        Long appointmentId,
        String status,
        String total,
        String paid,
        String balance,
        List<LineView> lines,
        List<ReceiptView> receipts
    ) {}

    public record PaymentRequest(
        @NotBlank String amount,
        @NotBlank String method,
        @jakarta.validation.constraints.Size(max = 100) String transactionReference,
        @jakarta.validation.constraints.Size(max = 500) String note
    ) {}
}
