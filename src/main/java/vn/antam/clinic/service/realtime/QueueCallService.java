package vn.antam.clinic.service.realtime;

import java.util.Map;

/** Business contract; implemented by the Spring-managed QueueCallServiceImpl. */
public interface QueueCallService {
    public Map<String, Object> state(long appointmentId);

    public Map<String, Object> call(long appointmentId);
}
