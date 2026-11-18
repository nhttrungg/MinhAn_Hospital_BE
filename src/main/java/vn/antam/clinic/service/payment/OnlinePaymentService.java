package vn.antam.clinic.service.payment;

import java.math.BigDecimal;
import java.util.*;

/** Business contract; implemented by the Spring-managed OnlinePaymentServiceImpl. */
public interface OnlinePaymentService {
    public record Intent(
        long id,
        long invoiceId,
        String reference,
        String amount,
        String status,
        String expiresAt,
        Long receiptId,
        String image,
        boolean simulator
    ) {}

    public record Transfer(
        String transactionId,
        String content,
        BigDecimal amount,
        String accountNumber
    ) {}

    public Intent create(long invoiceId);

    public Intent get(long id);

    public Intent simulate(long id);

    public Transfer parse(byte[] body);

    public Map<String, Object> settle(Transfer transfer);

    public List<Map<String, Object>> mismatches();

    public void expire();
}
