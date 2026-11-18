package vn.antam.clinic.web.rest.realtime;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.antam.clinic.service.event.RealtimeHub;

@RestController
public class RealtimeResource {

    private final RealtimeHub hub;

    public RealtimeResource(RealtimeHub hub) {
        this.hub = hub;
    }

    @GetMapping(value = "/api/v1/realtime/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream() {
        return response(hub.staffStream());
    }

    static ResponseEntity<SseEmitter> response(SseEmitter stream) {
        return ResponseEntity.ok()
            .header("Cache-Control", "no-cache, no-store, no-transform")
            .header("X-Accel-Buffering", "no")
            .body(stream);
    }
}
