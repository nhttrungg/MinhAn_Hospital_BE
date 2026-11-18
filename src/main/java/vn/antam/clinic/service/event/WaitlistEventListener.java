package vn.antam.clinic.service.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.antam.clinic.domain.event.ClinicEvent;
import vn.antam.clinic.service.booking.WaitlistService;

@Component
public class WaitlistEventListener {

    private static final Logger log = LoggerFactory.getLogger(WaitlistEventListener.class);
    private final WaitlistService waitlist;

    public WaitlistEventListener(WaitlistService waitlist) {
        this.waitlist = waitlist;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ClinicEvent event) {
        if (!event.type().equals("slot.freed")) return;
        if (!(event.data().get("slotId") instanceof Number slot)) return;
        Object key = event.data().get("releaseKey");
        try {
            if (key == null) waitlist.slotFreed(slot.longValue());
            else waitlist.slotFreed(slot.longValue(), String.valueOf(key));
        } catch (RuntimeException error) {
            // The booking commit has already succeeded; never report its cancellation as failed.
            log.error("Waitlist delivery failed for slot {}", slot, error);
            // The periodic delivery retry will pick this release up again.
        }
    }
}
