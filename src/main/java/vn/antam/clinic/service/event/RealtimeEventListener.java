package vn.antam.clinic.service.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.antam.clinic.domain.event.ClinicEvent;

@Component
public class RealtimeEventListener {

    private final RealtimeBroadcaster hub;

    public RealtimeEventListener(RealtimeBroadcaster hub) {
        this.hub = hub;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ClinicEvent event) {
        hub.publish(event);
    }
}
