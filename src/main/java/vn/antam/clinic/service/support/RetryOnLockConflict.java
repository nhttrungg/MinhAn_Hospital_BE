package vn.antam.clinic.service.support;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RetryOnLockConflict {
    int max() default 3;
}
