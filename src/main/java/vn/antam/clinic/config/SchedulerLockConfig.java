package vn.antam.clinic.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("!test")
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulerLockConfig {

    @Bean
    LockProvider scheduledLocks(DataSource source) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(source))
                .usingDbTime()
                .build()
        );
    }
}
