package vn.antam.clinic.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${app.zone:Asia/Ho_Chi_Minh}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
