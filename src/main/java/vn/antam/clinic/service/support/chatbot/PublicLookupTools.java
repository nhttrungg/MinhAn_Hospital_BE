package vn.antam.clinic.service.support.chatbot;

import static vn.antam.clinic.service.dto.chatbot.ChatTypes.*;

import java.text.Normalizer;
import java.time.*;
import java.util.*;

public final class PublicLookupTools {

    private PublicLookupTools() {}

    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .toLowerCase(Locale.ROOT);
    }

    public static List<Map<String, Object>> tools() {
        return List.of(
            Map.of(
                "type",
                "function",
                "function",
                Map.of(
                    "name",
                    "public_lookup",
                    "description",
                    "Đọc thông tin công khai: bác sĩ, giá, chuyên khoa, lịch, liên hệ. CARE hỗ trợ chọn nơi khám theo nhu cầu từ danh sách khoa được cung cấp, không chẩn đoán hay điều trị. Không đặt lịch.",
                    "parameters",
                    Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of(
                            "kind",
                            Map.of(
                                "type",
                                "string",
                                "enum",
                                List.of("DOCTORS", "SPECIALTIES", "SLOTS", "CLINIC", "CARE")
                            ),
                            "keyword",
                            Map.of(
                                "type",
                                "string",
                                "description",
                                "Tên bác sĩ/chuyên khoa khách hỏi. Riêng CARE: tên khoa chính xác trong danh sách đang công khai phù hợp nhu cầu; bỏ trống nếu chưa đủ rõ. Không tự tạo khoa hay ID."
                            ),
                            "gender",
                            Map.of(
                                "type",
                                "string",
                                "enum",
                                List.of("MALE", "FEMALE", "OTHER"),
                                "description",
                                "Giới tính bác sĩ theo yêu cầu, tách khỏi tên/khoa. Bỏ trống nếu không có điều kiện, giữ điều kiện của cuộc hội thoại khi hỏi tiếp."
                            ),
                            "resetFilters",
                            Map.of(
                                "type",
                                "boolean",
                                "description",
                                "true khi khách bắt đầu tìm nhu cầu mới và muốn bỏ bộ lọc cũ."
                            ),
                            "clearFilters",
                            Map.of(
                                "type",
                                "array",
                                "items",
                                Map.of(
                                    "type",
                                    "string",
                                    "enum",
                                    List.of("keyword", "gender", "date")
                                ),
                                "description",
                                "Các điều kiện khách muốn bỏ. Ví dụ không phân biệt nam/nữ: bỏ gender."
                            ),
                            "urgent",
                            Map.of(
                                "type",
                                "boolean",
                                "description",
                                "Chỉ dùng với CARE: true nếu khách mô tả dấu hiệu nguy hiểm cần trợ giúp y tế ngay; không kết luận bệnh."
                            ),
                            "date",
                            Map.of(
                                "type",
                                "string",
                                "description",
                                "Ngày yyyy-MM-dd theo giờ Việt Nam; bỏ trống nếu khách chưa chọn ngày."
                            )
                        ),
                        "required",
                        List.of("kind"),
                        "additionalProperties",
                        false
                    )
                )
            )
        );
    }
}
