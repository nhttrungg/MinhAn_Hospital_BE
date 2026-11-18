package vn.antam.clinic.service.support.payment;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.antam.clinic.web.rest.errors.ApiException;

@Component
public class PaymentWebhookSignature {

    private final String secret;

    public PaymentWebhookSignature(@Value("${app.payments.webhook-secret:}") String secret) {
        this.secret = secret;
    }

    public void verify(byte[] body, String header) {
        if (
            secret.isBlank() || header == null || !header.matches("(?:sha256=)?[0-9a-fA-F]{64}")
        ) throw denied();
        String hex = header.startsWith("sha256=") ? header.substring(7) : header;
        if (
            !MessageDigest.isEqual(sign(body, secret), HexFormat.of().parseHex(hex))
        ) throw denied();
    }

    public static byte[] sign(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Webhook signature unavailable", e);
        }
    }

    private static ApiException denied() {
        return new ApiException(
            HttpStatus.UNAUTHORIZED,
            "Chữ ký không hợp lệ",
            "Không xác thực được thông báo thanh toán."
        );
    }
}
