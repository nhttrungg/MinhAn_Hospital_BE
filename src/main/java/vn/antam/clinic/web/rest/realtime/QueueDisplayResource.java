package vn.antam.clinic.web.rest.realtime;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.antam.clinic.service.event.RealtimeHub;
import vn.antam.clinic.service.realtime.QueueCallService;
import vn.antam.clinic.service.realtime.QueueDisplayService;
import vn.antam.clinic.service.support.QueueDisplayText;

@RestController
public class QueueDisplayResource {

    private final QueueDisplayService display;
    private final QueueCallService calls;
    private final RealtimeHub hub;

    public QueueDisplayResource(
        QueueDisplayService display,
        QueueCallService calls,
        RealtimeHub hub
    ) {
        this.display = display;
        this.calls = calls;
        this.hub = hub;
    }

    @GetMapping("/api/v1/public/queue-display")
    public ResponseEntity<QueueDisplayService.Board> board(
        @RequestParam(required = false) String rooms
    ) {
        return ResponseEntity.ok()
            .header("Cache-Control", "no-store")
            .body(display.board(QueueDisplayText.parseRooms(rooms)));
    }

    @GetMapping(
        value = "/api/v1/public/queue-display/stream",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<SseEmitter> stream(@RequestParam(required = false) String rooms) {
        return RealtimeResource.response(hub.publicStream(QueueDisplayText.parseRooms(rooms)));
    }

    @PostMapping("/api/v1/queue/{appointmentId}/call")
    public Map<String, Object> call(@PathVariable long appointmentId) {
        return calls.call(appointmentId);
    }

    @GetMapping("/api/v1/queue/{appointmentId}/call-state")
    public Map<String, Object> state(@PathVariable long appointmentId) {
        return calls.state(appointmentId);
    }
}
