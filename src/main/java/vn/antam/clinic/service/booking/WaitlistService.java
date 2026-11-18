package vn.antam.clinic.service.booking;

import java.util.List;
import vn.antam.clinic.service.dto.booking.WaitlistDtos.*;

/** Business contract; implemented by the Spring-managed WaitlistServiceImpl. */
public interface WaitlistService {
    public Entry join(JoinRequest request);

    public List<Entry> mine();

    public void cancel(long id);

    public void slotFreed(long slotId);

    public void slotFreed(long slotId, String releaseKey);

    public void booked(long patient, long slotId);

    public void expire();
}
