package vn.antam.clinic.service.impl.chatbot;

import static vn.antam.clinic.service.dto.chatbot.ChatTypes.*;
import static vn.antam.clinic.service.support.ClinicJdbc.bad;
import static vn.antam.clinic.service.support.chatbot.PublicLookupTools.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.antam.clinic.service.booking.BookingService;
import vn.antam.clinic.service.booking.PublicClinicService;
import vn.antam.clinic.service.chatbot.PublicLookup;

/** Only public, read-only service calls. No patient data or arbitrary SQL/URLs. */
@Service
public class PublicLookupImpl implements PublicLookup {

    private final BookingService bookings;
    private final PublicClinicService clinic;
    private final Clock clock;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public PublicLookupImpl(BookingService bookings, PublicClinicService clinic, Clock clock) {
        this.bookings = bookings;
        this.clinic = clinic;
        this.clock = clock;
    }

    @Override
    public boolean demo() {
        return clinic.clinic().demoData();
    }

    private Long id(JsonNode args, String name) {
        if (!args.has(name) || args.path(name).isNull()) return null;
        if (!args.path(name).isIntegralNumber() || args.path(name).asLong() < 1) throw bad(
            "Bộ lọc tra cứu không hợp lệ."
        );
        return args.path(name).asLong();
    }

    @Override
    public List<Map<String, String>> careCatalog() {
        return bookings
            .specialties()
            .stream()
            .map(s ->
                Map.of(
                    "name",
                    s.name(),
                    "description",
                    s.description() == null
                        ? ""
                        : s.description().substring(0, Math.min(s.description().length(), 1500))
                )
            )
            .toList();
    }

    private Result care(String keyword, String date, boolean urgent, String gender) {
        if (urgent) return new Result(
            "Các dấu hiệu bạn nêu cần được nhân viên y tế đánh giá ngay. Hãy tìm trợ giúp y tế hoặc đến cơ sở cấp cứu; không chờ lịch khám thường. Tôi không thể xác định bệnh qua chat.",
            List.of(),
            List.of(new Action("Thông tin liên hệ cơ sở", "/contact", null))
        );
        var available = bookings.specialties();
        var selected = available
            .stream()
            .filter(
                s -> !keyword.isBlank() && normalize(s.name()).equals(normalize(keyword.strip()))
            )
            .toList();
        if (selected.size() != 1) return new Result(
            "Chưa đủ thông tin để chọn một khoa phù hợp đang có tại cơ sở. Bạn đang gặp vấn đề ở vị trí nào, kéo dài bao lâu? Bạn cũng có thể liên hệ lễ tân để được hướng dẫn." +
                (date.isBlank() ? "" : " Ngày bạn muốn khám: " + date) +
                ".",
            List.of(),
            List.of(
                new Action(
                    "Xem chuyên khoa và lịch khám",
                    "/booking" + (date.isBlank() ? "" : "?date=" + date),
                    null
                ),
                new Action("Liên hệ lễ tân", "/contact", null)
            )
        );
        var specialty = selected.getFirst();
        String answer =
            "Bạn có thể tham khảo khoa " +
            specialty.name() +
            " để bác sĩ thăm khám ban đầu và hướng dẫn chuyển khoa khi cần. Đây là gợi ý nơi khám, chưa phải kết luận bệnh.";
        if (!gender.isBlank()) {
            var filters = new com.fasterxml.jackson.databind.ObjectMapper()
                .createObjectNode()
                .put("kind", date.isBlank() ? "DOCTORS" : "SLOTS")
                .put("keyword", specialty.name())
                .put("gender", gender);
            if (!date.isBlank()) filters.put("date", date);
            var found = execute(filters);
            return new Result(answer + "\n" + found.answer(), found.sources(), found.actions());
        }
        if (!date.isBlank()) answer +=
            " Tôi đã giữ ngày " +
            LocalDate.parse(date).format(
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
            ) +
            " trong nút tìm lịch bên dưới; lịch trống được kiểm tra trên trang đặt khám.";
        answer +=
            " Nếu đau đột ngột hoặc dữ dội, nôn ra máu, đi ngoài phân đen, đau ngực hay khó thở, hãy tìm trợ giúp y tế ngay, không chờ lịch hẹn.";
        return new Result(
            answer,
            List.of(
                new Source(
                    "specialty-" + specialty.id(),
                    specialty.name(),
                    "/specialties/" + specialty.id()
                )
            ),
            List.of(
                new Action(
                    "Tìm lịch khoa " + specialty.name(),
                    "/booking?specialtyId=" +
                        specialty.id() +
                        (date.isBlank() ? "" : "&date=" + date),
                    null
                )
            )
        );
    }

    @Override
    public Result execute(JsonNode args) {
        if (args == null || !args.isObject()) throw bad("Tham số tra cứu không hợp lệ.");
        String kind = args.path("kind").asText(),
            keyword = args.path("keyword").asText("");
        if (keyword.length() > 200) throw bad("Từ khóa tối đa 200 ký tự.");
        String gender = args.path("gender").asText("");
        if (!gender.isBlank() && !Set.of("MALE", "FEMALE", "OTHER").contains(gender)) throw bad(
            "Giới tính cần là MALE, FEMALE hoặc OTHER."
        );
        if ("CARE".equals(kind)) {
            String date = args.path("date").asText("");
            if (
                !date.isBlank() &&
                (!date.matches("\\d{4}-\\d{2}-\\d{2}") || CareNavigation.date(date, clock) == null)
            ) throw bad("Ngày khám phải có dạng yyyy-MM-dd.");
            return care(keyword, date, args.path("urgent").asBoolean(false), gender);
        }
        Long doctor = id(args, "doctorId"),
            specialty = id(args, "specialtyId"),
            service = id(args, "serviceId");
        if ("SLOTS".equals(kind) && doctor == null && specialty == null && !keyword.isBlank()) {
            var exact = bookings
                .specialties()
                .stream()
                .filter(s -> normalize(s.name()).equals(normalize(keyword.strip())))
                .toList();
            if (exact.size() == 1) specialty = exact.getFirst().id();
        }
        List<Action> actions = new ArrayList<>();
        List<Source> sources = new ArrayList<>();
        if ("CLINIC".equals(kind)) {
            var c = clinic.clinic();
            return new Result(
                c.name() +
                    "\nĐịa chỉ: " +
                    value(c.address()) +
                    "\nĐiện thoại: " +
                    value(c.phone()) +
                    "\nEmail: " +
                    value(c.email()) +
                    "\nGiờ làm việc: " +
                    value(c.workingHours()) +
                    "\nWebsite: " +
                    value(c.website()) +
                    c
                        .contactPhones()
                        .stream()
                        .map(p -> "\n" + p.label() + ": " + p.phone())
                        .collect(java.util.stream.Collectors.joining()),
                List.of(new Source("clinic", "Thông tin cơ sở", "/contact")),
                List.of(new Action("Liên hệ cơ sở", "/contact", null))
            );
        }
        if ("SPECIALTIES".equals(kind)) {
            final Long requestedSpecialty = specialty;
            var matches = bookings
                .specialties()
                .stream()
                .filter(
                    s ->
                        (requestedSpecialty == null || s.id() == requestedSpecialty) &&
                        (keyword.isBlank() || normalize(s.name()).contains(normalize(keyword)))
                )
                .toList();
            // A keyword search stays short. Listing the catalog must not drop later specialties.
            boolean partial = !keyword.isBlank() && matches.size() > 12;
            if (partial) matches = matches.subList(0, 12);
            for (var s : matches) {
                actions.add(new Action(s.name(), "/specialties/" + s.id(), s.description()));
                sources.add(new Source("specialty-" + s.id(), s.name(), "/specialties/" + s.id()));
            }
            return new Result(
                matches.isEmpty()
                    ? "Chưa tìm thấy chuyên khoa phù hợp. Bạn có thể kiểm tra lại tên."
                    : "Các chuyên khoa đang công khai:\n" +
                          String.join(
                              "\n",
                              matches
                                  .stream()
                                  .map(s -> s.name())
                                  .toList()
                          ) +
                          (partial
                              ? "\nCòn chuyên khoa khác. Mở trang Chuyên khoa để xem đủ."
                              : ""),
                sources,
                actions
            );
        }
        final Long requestedDoctor = doctor;
        String key = normalize(keyword);
        // "Nội" names the general specialty, not every name that merely contains that syllable.
        var listedSpecialties = key.isBlank() || specialty != null ? null : bookings.specialties();
        final boolean namedSpecialty =
            listedSpecialties != null &&
            listedSpecialties.stream().anyMatch(s -> {
                String name = normalize(s.name());
                return name.equals(key) || name.equals(key + " tong quat");
            });
        var doctors = bookings
            .doctors(specialty)
            .stream()
            .filter(
                d ->
                    (requestedDoctor == null || d.id() == requestedDoctor) &&
                    (service == null || d.serviceId() == service) &&
                    (gender.isBlank() || gender.equals(d.gender())) &&
                    (key.isBlank() ||
                        (namedSpecialty
                            ? normalize(d.specialtyName()).equals(key) ||
                              normalize(d.specialtyName()).equals(key + " tong quat") ||
                              normalize(d.fullName()).contains(key)
                            : normalize(
                                  d.fullName() + " " + d.specialtyName() + " " + d.serviceName()
                              ).contains(key)))
            )
            .toList();
        if ("DOCTORS".equals(kind)) {
            String selectedDate = args.path("date").asText("");
            if (
                !selectedDate.isBlank() &&
                (!selectedDate.matches("\\d{4}-\\d{2}-\\d{2}") ||
                    CareNavigation.date(selectedDate, clock) == null)
            ) throw bad("Ngày khám phải có dạng yyyy-MM-dd.");
            var matches = doctors.stream().limit(12).toList();
            for (var d : matches) {
                String detail =
                    d.specialtyName() + " · " + d.serviceName() + " · " + price(d.price());
                actions.add(
                    new Action(
                        "Đặt khám với " + d.fullName(),
                        "/booking?doctorId=" +
                            d.id() +
                            "&serviceId=" +
                            d.serviceId() +
                            "&specialtyId=" +
                            d.specialtyId() +
                            (selectedDate.isBlank() ? "" : "&date=" + selectedDate),
                        detail
                    )
                );
                if (sources.stream().noneMatch(s -> s.id().equals("doctor-" + d.id()))) sources.add(
                    new Source("doctor-" + d.id(), d.fullName(), "/doctors/" + d.id())
                );
            }
            return new Result(
                matches.isEmpty()
                    ? gender.isBlank()
                        ? "Chưa tìm thấy bác sĩ hoặc dịch vụ phù hợp. Bạn có thể kiểm tra lại tên."
                        : "Chưa tìm thấy bác sĩ " +
                          genderLabel(gender) +
                          " có thông tin giới tính đã khai báo phù hợp. Bạn có thể liên hệ lễ tân để được hỗ trợ."
                    : (gender.isBlank()
                          ? "Thông tin và giá khám hiện hành:"
                          : "Bác sĩ " + genderLabel(gender) + " phù hợp và giá khám hiện hành:") +
                          "\n" +
                          String.join(
                              "\n",
                              matches
                                  .stream()
                                  .map(
                                      d ->
                                          d.fullName() +
                                          " · " +
                                          d.serviceName() +
                                          ": " +
                                          price(d.price())
                                  )
                                  .toList()
                          ) +
                          "\nGiá theo lượt; tiền thuốc không nằm trong hóa đơn khám.",
                sources,
                actions
            );
        }
        if (!"SLOTS".equals(kind)) throw bad("Công cụ tra cứu không được hỗ trợ.");
        if (!keyword.isBlank() && doctor == null && specialty == null) {
            var people = doctors
                .stream()
                .map(d -> d.id())
                .distinct()
                .toList();
            if (people.size() == 1) doctor = people.getFirst();
            else {
                for (var d : doctors.stream().limit(6).toList())
                    actions.add(
                        new Action(
                            "Chọn " + d.fullName(),
                            "/booking?doctorId=" + d.id(),
                            d.specialtyName()
                        )
                    );
                return new Result(
                    people.isEmpty()
                        ? "Chưa tìm thấy bác sĩ theo tên này. Bạn muốn khám chuyên khoa nào?"
                        : "Có nhiều bác sĩ phù hợp. Bạn muốn xem lịch của bác sĩ nào?",
                    List.of(),
                    actions
                );
            }
        }
        LocalDate today = LocalDate.now(clock.withZone(ZONE)),
            date = today;
        String raw = args.path("date").asText("");
        if (!raw.isBlank()) {
            try {
                date = LocalDate.parse(raw);
            } catch (java.time.format.DateTimeParseException e) {
                throw bad("Ngày tra cứu phải có dạng yyyy-MM-dd.");
            }
            if (date.isBefore(today) || date.isAfter(today.plusDays(366))) throw bad(
                "Chọn ngày từ hôm nay đến tối đa một năm tới."
            );
        }
        var from = date.atStartOfDay(ZONE).toInstant();
        var to = date
            .plusDays(raw.isBlank() ? 14 : 1)
            .atStartOfDay(ZONE)
            .toInstant();
        var slots = (
            gender.isBlank()
                ? bookings.availableSlots(from, to, doctor, service, specialty)
                : bookings.availableSlots(from, to, doctor, service, specialty, gender)
        )
            .stream()
            .limit(8)
            .toList();
        var fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZONE);
        for (var s : slots) {
            String day = s.startsAt().atZone(ZONE).toLocalDate().toString();
            actions.add(
                new Action(
                    "Chọn lịch " + s.doctorName(),
                    "/booking?doctorId=" +
                        s.doctorId() +
                        "&serviceId=" +
                        s.serviceId() +
                        "&date=" +
                        day +
                        (specialty == null ? "" : "&specialtyId=" + specialty),
                    fmt.format(s.startsAt()) + " · " + price(s.price())
                )
            );
        }
        String filter =
            (doctor == null ? "" : "doctorId=" + doctor) +
            (specialty == null ? "" : (doctor == null ? "" : "&") + "specialtyId=" + specialty);
        sources.add(
            new Source(
                "slots",
                "Lịch khám đang công bố",
                "/booking" + (filter.isEmpty() ? "" : "?" + filter)
            )
        );
        return new Result(
            slots.isEmpty()
                ? "Chưa có lịch trống phù hợp. Hãy chọn ngày hoặc bác sĩ khác."
                : "Một số lịch trống " +
                      (raw.isBlank() ? "trong 14 ngày tới" : "ngày " + date) +
                      " (giờ Việt Nam):\n" +
                      String.join(
                          "\n",
                          slots
                              .stream()
                              .map(
                                  s ->
                                      s.doctorName() +
                                      " · " +
                                      fmt.format(s.startsAt()) +
                                      " · " +
                                      price(s.price())
                              )
                              .toList()
                      ) +
                      "\nLịch có thể thay đổi; hệ thống sẽ kiểm tra lại khi bạn xác nhận đặt khám.",
            sources,
            actions
        );
    }

    private static String value(String s) {
        return s == null || s.isBlank() ? "Chưa công bố" : s;
    }

    private static String genderLabel(String gender) {
        return switch (gender) {
            case "FEMALE" -> "nữ";
            case "MALE" -> "nam";
            default -> "có giới tính khác";
        };
    }

    private static String price(String s) {
        return s == null
            ? "Liên hệ cơ sở"
            : String.format(new Locale("vi", "VN"), "%,.0f ₫", new java.math.BigDecimal(s));
    }
}
