package vn.antam.clinic.service.notification;

import vn.antam.clinic.service.dto.shared.PageResponse;

/** Business contract; implemented by the Spring-managed AppointmentNotificationServiceImpl. */
public interface AppointmentNotificationService {
    public void enqueue(long appointment, String title);

    public void enqueue(
        long appointment,
        String kind,
        String title,
        String body,
        String link,
        String dedupeKey
    );

    public void notify(long userId, String kind, String title, String body, String link);

    public void notify(
        long userId,
        String kind,
        String title,
        String body,
        String link,
        String dedupeKey
    );

    public record Notice(
        long id,
        Long appointmentId,
        String title,
        String body,
        String createdAt,
        boolean read,
        String kind,
        String link
    ) {}

    public PageResponse<Notice> list(int page, int size);

    public void read(long id);
}
