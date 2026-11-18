package vn.antam.clinic.security;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.antam.clinic.web.rest.errors.ApiException;

public class BoundedPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;
    private final Semaphore permits;

    public BoundedPasswordEncoder(PasswordEncoder delegate, int concurrency) {
        this.delegate = delegate;
        permits = new Semaphore(Math.max(1, concurrency), true);
    }

    private <T> T run(Supplier<T> task) {
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(2, TimeUnit.SECONDS);
            if (!acquired) throw busy();
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw busy();
        } finally {
            if (acquired) permits.release();
        }
    }

    private static ApiException busy() {
        return new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Hệ thống đang bận",
            "Vui lòng thử lại sau vài giây."
        );
    }

    @Override
    public String encode(CharSequence raw) {
        return run(() -> delegate.encode(raw));
    }

    @Override
    public boolean matches(CharSequence raw, String encoded) {
        return run(() -> delegate.matches(raw, encoded));
    }

    @Override
    public boolean upgradeEncoding(String encoded) {
        return delegate.upgradeEncoding(encoded);
    }
}
