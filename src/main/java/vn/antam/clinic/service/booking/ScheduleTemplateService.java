package vn.antam.clinic.service.booking;

import java.util.List;
import vn.antam.clinic.service.dto.booking.BookingDtos.AppointmentView;
import vn.antam.clinic.service.dto.booking.BookingDtos.SlotView;
import vn.antam.clinic.service.dto.booking.ScheduleTemplateDtos.CopyRequest;
import vn.antam.clinic.service.dto.booking.ScheduleTemplateDtos.CreateTemplateRequest;
import vn.antam.clinic.service.dto.booking.ScheduleTemplateDtos.GenerateRequest;
import vn.antam.clinic.service.dto.booking.ScheduleTemplateDtos.TemplateView;

/** Business contract; implemented by the Spring-managed ScheduleTemplateServiceImpl. */
public interface ScheduleTemplateService {
    public List<TemplateView> list();

    public TemplateView create(CreateTemplateRequest request);

    public List<SlotView> generate(long id, GenerateRequest request);

    public TemplateView update(long id, CreateTemplateRequest request);

    public void disable(long id);

    public List<SlotView> copy(long id, CopyRequest request);

    public List<AppointmentView> affected(long slotId);
}
