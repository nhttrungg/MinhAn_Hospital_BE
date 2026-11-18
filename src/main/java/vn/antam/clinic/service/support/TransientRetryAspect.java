package vn.antam.clinic.service.support;

import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Outside transaction advice: each attempt starts and rolls back its own transaction. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TransientRetryAspect {

    @Around("@annotation(retry)")
    public Object retry(ProceedingJoinPoint point, RetryOnLockConflict retry) throws Throwable {
        if (
            org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
        ) return point.proceed();
        for (int attempt = 1; ; attempt++) {
            try {
                return point.proceed();
            } catch (RuntimeException failure) {
                if (attempt >= retry.max() || !lockConflict(failure)) throw failure;
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(50, 201));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
        }
    }

    static boolean lockConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (
                cause instanceof org.springframework.dao.CannotAcquireLockException ||
                cause instanceof org.springframework.dao.PessimisticLockingFailureException
            ) return true;
            if (
                cause instanceof SQLException sql &&
                (sql.getErrorCode() == 1213 || sql.getErrorCode() == 1205)
            ) return true;
        }
        return false;
    }
}
