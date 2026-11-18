package vn.antam.clinic.config;

import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.timelimiter.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import vn.antam.clinic.integration.ai.AiHttpClient;
import vn.antam.clinic.web.rest.errors.ApiException;

@Configuration
public class BulkheadConfig {

    private static final Map<String, Bulkhead> LIMITS = Map.of(
        "ai",
        limit("ai", 20),
        "qdrant",
        limit("qdrant", 20),
        "pdf",
        limit("pdf", 8),
        "vietqr",
        limit("vietqr", 10),
        "chat",
        limit("chat", 20)
    );
    private static final CircuitBreaker AI = CircuitBreaker.of(
        "ai",
        CircuitBreakerConfig.custom()
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .recordException(e -> e instanceof AiHttpClient.RemoteFailure f && f.retryable)
            .build()
    );
    private static final ExecutorService PDF = new ThreadPoolExecutor(
        8,
        8,
        0,
        TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        r -> {
            var t = new Thread(r, "clinic-pdf");
            t.setDaemon(true);
            return t;
        },
        new ThreadPoolExecutor.AbortPolicy()
    );
    private static final TimeLimiter PDF_TIMEOUT = TimeLimiter.of(Duration.ofSeconds(10));

    private static Bulkhead limit(String name, int size) {
        return Bulkhead.of(
            name,
            io.github.resilience4j.bulkhead.BulkheadConfig.custom()
                .maxConcurrentCalls(size)
                .maxWaitDuration(Duration.ZERO)
                .build()
        );
    }

    public static <T> T run(String name, Supplier<T> action) {
        try {
            return LIMITS.get(name).executeSupplier(action);
        } catch (BulkheadFullException unavailable) {
            throw busy();
        }
    }

    public static <T> T ai(Supplier<T> action) {
        try {
            return run("ai", () -> AI.executeSupplier(action));
        } catch (CallNotPermittedException unavailable) {
            throw new AiHttpClient.RemoteFailure("AI đang tạm gián đoạn.", true);
        }
    }

    public static byte[] pdf(Supplier<byte[]> render) {
        try {
            return PDF_TIMEOUT.executeFutureSupplier(() -> PDF.submit(() -> run("pdf", render)));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw busy();
        } catch (RuntimeException e) {
            throw e instanceof RejectedExecutionException ? busy() : e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw busy();
        } catch (Exception e) {
            throw busy();
        }
    }

    private static ApiException busy() {
        return new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Hệ thống đang bận",
            "Vui lòng thử lại sau vài giây."
        );
    }
}
