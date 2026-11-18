package vn.antam.clinic.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.antam.clinic.service.event.RealtimeHub;

@Configuration
public class RealtimeMetricsConfig {

    @Bean
    MeterBinder realtimeMetrics(
        RealtimeHub hub,
        vn.antam.clinic.security.SessionStateCache states
    ) {
        return registry -> {
            Gauge.builder("antam.realtime.connections", hub, RealtimeHub::connectionCount)
                .description("Open local SSE connections")
                .register(registry);
            Gauge.builder("antam.realtime.pending.sends", hub, RealtimeHub::pendingSendCount)
                .description("Queued local SSE sends")
                .register(registry);
            io.micrometer.core.instrument.FunctionCounter.builder(
                "antam.realtime.send.rejected",
                hub,
                RealtimeHub::droppedSendCount
            ).register(registry);
            io.micrometer.core.instrument.FunctionCounter.builder(
                "antam.session.state.hits",
                states,
                s -> s.stats().hitCount()
            ).register(registry);
            io.micrometer.core.instrument.FunctionCounter.builder(
                "antam.session.state.misses",
                states,
                s -> s.stats().missCount()
            ).register(registry);
            Gauge.builder("antam.realtime.capacity", hub, RealtimeHub::capacity).register(registry);
        };
    }
}
