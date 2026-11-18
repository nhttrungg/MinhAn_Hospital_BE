package vn.antam.clinic.service.support.payment;

import static vn.antam.clinic.service.support.ClinicJdbc.bad;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** VietQR NAPAS 247 account transfer payload (A000000727 / QRIBFTTA). */
@Component
public class EmvQrBuilder {

    public String build(
        String bin,
        String account,
        String name,
        BigDecimal amount,
        String reference
    ) {
        if (
            bin == null ||
            !bin.matches("[0-9]{6}") ||
            account == null ||
            !account.matches("[0-9]{6,19}")
        ) throw bad("Chưa cấu hình tài khoản ngân hàng hợp lệ.");
        if (
            amount == null ||
            amount.signum() <= 0 ||
            amount.stripTrailingZeros().scale() > 0 ||
            amount.compareTo(new BigDecimal("9999999999999")) > 0
        ) throw bad("QR chỉ nhận số tiền VND nguyên hợp lệ.");
        if (reference == null || !reference.matches("AT[A-Z0-9]{8}")) throw bad(
            "Nội dung chuyển khoản không hợp lệ."
        );
        String merchant = Normalizer.normalize(
            name == null ? "MINH AN CLINIC" : name,
            Normalizer.Form.NFD
        )
            .replaceAll("\\p{M}", "")
            .replace('Đ', 'D')
            .replace('đ', 'd')
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9 ]", "")
            .trim();
        if (merchant.isBlank()) merchant = "MINH AN CLINIC";
        if (merchant.length() > 25) merchant = merchant.substring(0, 25);
        String beneficiary = tlv("00", bin) + tlv("01", account);
        String transfer = tlv("00", "A000000727") + tlv("01", beneficiary) + tlv("02", "QRIBFTTA");
        String payload =
            tlv("00", "01") +
            tlv("01", "12") +
            tlv("38", transfer) +
            tlv("53", "704") +
            tlv("54", amount.toBigIntegerExact().toString()) +
            tlv("58", "VN") +
            tlv("59", merchant) +
            tlv("62", tlv("08", reference)) +
            "6304";
        return payload + String.format(Locale.ROOT, "%04X", crc(payload));
    }

    public static String tlv(String tag, String value) {
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        if (length > 99) throw bad("Trường QR quá dài.");
        return tag + String.format(Locale.ROOT, "%02d", length) + value;
    }

    public static int crc(String value) {
        int crc = 0xffff;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 255) << 8;
            for (int i = 0; i < 8; i++) crc =
                ((crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1) & 0xffff;
        }
        return crc;
    }
}
