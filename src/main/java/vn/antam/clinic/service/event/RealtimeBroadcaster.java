package vn.antam.clinic.service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.antam.clinic.domain.event.ClinicEvent;

/** Pub/sub carries routing IDs only; clients receive the hub's empty invalidate signal. */
@Component
public class RealtimeBroadcaster implements MessageListener {

    private final RealtimeHub hub;
    private final StringRedisTemplate redis;
    private final String origin = UUID.randomUUID().toString(),
        channel;
    private final ObjectMapper json = new ObjectMapper();

    public record Wire(String origin, String type, Long roomId, Long doctorId, Long userId) {}

    public RealtimeBroadcaster(
        RealtimeHub hub,
        StringRedisTemplate redis,
        @Value("${app.redis.channel-prefix:antam}") String prefix
    ) {
        this.hub = hub;
        this.redis = redis;
        channel = prefix + ":events";
    }

    public String channel() {
        return channel;
    }

    public void publish(ClinicEvent event) {
        hub.publish(event);
        try {
            redis.convertAndSend(
                channel,
                json.writeValueAsString(
                    new Wire(
                        origin,
                        event.type(),
                        event.roomId(),
                        event.doctorStaffId(),
                        event.userId()
                    )
                )
            );
        } catch (Exception unavailable) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                "Realtime publication failed; local clients have received the event",
                unavailable
            );
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Wire wire = json.readValue(
                new String(message.getBody(), StandardCharsets.UTF_8),
                Wire.class
            );
            if (!origin.equals(wire.origin())) hub.publish(
                new ClinicEvent(
                    wire.type(),
                    wire.roomId(),
                    wire.doctorId(),
                    wire.userId(),
                    Map.of()
                )
            );
        } catch (Exception malformed) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Invalid realtime envelope ignored");
        }
    }
}
