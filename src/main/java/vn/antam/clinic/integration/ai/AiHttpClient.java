package vn.antam.clinic.integration.ai;

import static vn.antam.clinic.service.support.ClinicJdbc.bad;

import com.fasterxml.jackson.databind.*;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import vn.antam.clinic.service.chatbot.AiConfigService;

@Component
public class AiHttpClient {

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    public final ObjectMapper json = new ObjectMapper();

    public static final class RemoteFailure extends RuntimeException {

        public final boolean retryable;

        public RemoteFailure(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {

        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        public CompletionStage<byte[]> getBody() {
            return body;
        }

        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 4_000_000 - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(
                        new RemoteFailure("Phản hồi AI/vector vượt giới hạn.", false)
                    );
                    return;
                }
                byte[] part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }

        public void onError(Throwable failure) {
            body.completeExceptionally(failure);
        }

        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }

    public static String base(String raw) {
        if (raw == null) throw bad("Thiếu URL gateway.");
        String b = raw.strip().replaceAll("/+$", "");
        try {
            URI u = URI.create(b);
            if (
                u.getScheme() == null ||
                !Set.of("http", "https").contains(u.getScheme()) ||
                u.getHost() == null ||
                u.getUserInfo() != null ||
                u.getQuery() != null ||
                u.getFragment() != null ||
                !u.getPath().endsWith("/v1")
            ) throw new IllegalArgumentException();
            return b;
        } catch (IllegalArgumentException e) {
            throw bad(
                "URL gateway phải là http(s), kết thúc /v1, không chứa tài khoản hoặc query."
            );
        }
    }

    public JsonNode request(
        String url,
        String key,
        String method,
        Object body,
        Duration timeout,
        boolean qdrant
    ) {
        Duration deadline =
            timeout.compareTo(Duration.ofSeconds(qdrant ? 3 : 30)) > 0
                ? Duration.ofSeconds(qdrant ? 3 : 30)
                : timeout;
        java.util.function.Supplier<JsonNode> task = () ->
            requestLimited(url, key, method, body, deadline, qdrant);
        return qdrant
            ? vn.antam.clinic.config.BulkheadConfig.run("qdrant", task)
            : vn.antam.clinic.config.BulkheadConfig.ai(task);
    }

    private JsonNode requestLimited(
        String url,
        String key,
        String method,
        Object body,
        Duration timeout,
        boolean qdrant
    ) {
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Accept", "application/json");
            if (key != null && !key.isBlank()) b.header(
                qdrant ? "api-key" : "Authorization",
                qdrant ? key : "Bearer " + key
            );
            if (body == null) b.method(method, HttpRequest.BodyPublishers.noBody());
            else b.header("Content-Type", "application/json").method(
                method,
                HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))
            );
            pending = client.sendAsync(b.build(), info -> new BoundedBody());
            // The deadline includes the body; receiving headers must not leave a blocking stream open.
            HttpResponse<byte[]> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            if (status < 200 || status >= 300) throw new RemoteFailure(
                "Dịch vụ AI/vector trả HTTP " + status + ".",
                status == 429 || status >= 500
            );
            byte[] bytes = response.body();
            return bytes.length == 0 ? json.createObjectNode() : json.readTree(bytes);
        } catch (RemoteFailure e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteFailure("Yêu cầu đã bị ngắt.", true);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RemoteFailure failure) throw failure;
            throw new RemoteFailure(
                "Không kết nối được AI/vector hoặc phản hồi không hợp lệ.",
                true
            );
        } catch (Exception e) {
            throw new RemoteFailure(
                "Không kết nối được AI/vector hoặc phản hồi không hợp lệ.",
                true
            );
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    public List<String> models(String base, String key) {
        JsonNode data = request(
            base + "/models",
            key,
            "GET",
            null,
            Duration.ofSeconds(8),
            false
        ).path("data");
        if (!data.isArray()) throw new RemoteFailure(
            "Gateway không trả danh sách model hợp lệ.",
            false
        );
        List<String> result = new ArrayList<>();
        data.forEach(n -> {
            String id = n.path("id").asText();
            if (!id.isBlank()) result.add(id);
        });
        return result.stream().distinct().sorted().toList();
    }

    public List<List<Double>> embed(AiConfigService.Runtime config, List<String> texts) {
        JsonNode data = request(
            config.embeddingBase() + "/embeddings",
            config.embeddingKey(),
            "POST",
            Map.of("model", config.embeddingModel(), "input", texts),
            Duration.ofSeconds(15),
            false
        ).path("data");
        if (!data.isArray() || data.size() != texts.size()) throw new RemoteFailure(
            "Embedding không trả đủ vector.",
            false
        );
        List<List<Double>> vectors = new ArrayList<>(Collections.nCopies(texts.size(), null));
        for (JsonNode item : data) {
            int i = item.path("index").asInt(-1);
            JsonNode v = item.path("embedding");
            if (
                i < 0 ||
                i >= texts.size() ||
                vectors.get(i) != null ||
                !v.isArray() ||
                v.isEmpty() ||
                v.size() > 32768
            ) throw new RemoteFailure("Vector embedding không hợp lệ.", false);
            List<Double> values = new ArrayList<>();
            double norm = 0;
            for (JsonNode number : v) {
                double d = number.asDouble(Double.NaN);
                if (!number.isNumber() || !Double.isFinite(d)) throw new RemoteFailure(
                    "Vector chứa giá trị không hợp lệ.",
                    false
                );
                values.add(d);
                norm += d * d;
            }
            if (norm == 0 || !Double.isFinite(norm)) throw new RemoteFailure(
                "Vector embedding bằng 0 hoặc không hữu hạn.",
                false
            );
            if (
                config.dimensions() != null && v.size() != config.dimensions()
            ) throw new RemoteFailure(
                "Số chiều embedding đã thay đổi; cần kiểm tra và lập chỉ mục lại.",
                false
            );
            vectors.set(i, values);
        }
        if (vectors.stream().anyMatch(Objects::isNull)) throw new RemoteFailure(
            "Embedding thiếu vector.",
            false
        );
        return vectors;
    }

    public JsonNode chat(
        AiConfigService.Runtime c,
        List<Map<String, Object>> messages,
        List<Map<String, Object>> tools,
        Object choice
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", c.chatModel());
        body.put("messages", messages);
        body.put("temperature", 0);
        body.put("max_tokens", 1000);
        if (!tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", choice);
        }
        JsonNode m = request(
            c.chatBase() + "/chat/completions",
            c.chatKey(),
            "POST",
            body,
            Duration.ofSeconds(20),
            false
        )
            .path("choices")
            .path(0)
            .path("message");
        if (m.isMissingNode()) throw new RemoteFailure(
            "Gateway không trả nội dung chat hợp lệ.",
            false
        );
        return m;
    }

    public static String hash(String text) {
        try {
            return HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(
                    text.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
