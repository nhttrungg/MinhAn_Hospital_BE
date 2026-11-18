package vn.antam.clinic.service.support.billing;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VietQrGateway {

    private final String client, key;
    private final URI endpoint;
    private final ObjectMapper json = new ObjectMapper();

    public VietQrGateway(
        @Value("${app.vietqr.client-id:}") String client,
        @Value("${app.vietqr.api-key:}") String key,
        @Value("${app.vietqr.endpoint:https://api.vietqr.io/v2/generate}") String endpoint
    ) {
        this.client = client;
        this.key = key;
        this.endpoint = URI.create(endpoint);
    }

    public String generate(Map<String, Object> bank, long amount, String reference) {
        return vn.antam.clinic.config.BulkheadConfig.run("vietqr", () ->
            generateLimited(bank, amount, reference)
        );
    }

    private String generateLimited(Map<String, Object> bank, long amount, String reference) {
        if (client.isBlank() || key.isBlank()) throw conflict(
            "Chưa cấu hình VietQR phía máy chủ; có thể thu tiền mặt hoặc thử lại sau."
        );
        try {
            String body = json.writeValueAsString(
                Map.of(
                    "accountNo",
                    bank.get("account_no"),
                    "accountName",
                    bank.get("account_name"),
                    "acqId",
                    Integer.parseInt(bank.get("bank_bin").toString()),
                    "amount",
                    amount,
                    "addInfo",
                    reference,
                    "format",
                    "text",
                    "template",
                    "compact"
                )
            );
            var request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("x-client-id", client)
                .header("x-api-key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            var response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
            var result = json.readTree(response.body());
            if (
                response.statusCode() != 200 ||
                !result.path("code").asText().equals("00") ||
                result.path("data").path("qrCode").asText().isBlank()
            ) throw new IllegalStateException();
            return result.path("data").path("qrCode").asText();
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw conflict(
                "Không lấy được QR ngân hàng. Thử lại hoặc thu tiền mặt; chưa ghi nhận thanh toán."
            );
        }
    }
}
