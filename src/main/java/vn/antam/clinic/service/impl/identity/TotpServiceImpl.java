package vn.antam.clinic.service.impl.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import vn.antam.clinic.service.identity.TotpService;

/** RFC 6238 SHA-1, 30 second step. No secret values are logged. */
@Service
public class TotpServiceImpl implements TotpService {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final SecureRandom random = new SecureRandom();

    @Override
    public String secret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32(bytes);
    }

    public static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0,
            bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 255);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                out.append(ALPHABET.charAt((buffer >> bits) & 31));
            }
        }
        if (bits > 0) out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    private static byte[] decode(String secret) {
        var out = new java.io.ByteArrayOutputStream();
        int buffer = 0,
            bits = 0;
        for (char c : secret.toCharArray()) {
            int v = ALPHABET.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Invalid Base32");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 255);
            }
        }
        return out.toByteArray();
    }

    @Override
    public String code(String secret, long epochSeconds, int digits) {
        if (digits != 6 && digits != 8) throw new IllegalArgumentException("Invalid digits");
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decode(secret), "HmacSHA1"));
            byte[] h = mac.doFinal(
                ByteBuffer.allocate(8)
                    .putLong(epochSeconds / 30)
                    .array()
            );
            int offset = h[h.length - 1] & 15;
            int value = ByteBuffer.wrap(h, offset, 4).getInt() & 0x7fffffff;
            return String.format(
                Locale.ROOT,
                "%0" + digits + "d",
                value % (digits == 6 ? 1000000 : 100000000)
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP unavailable", e);
        }
    }

    @Override
    public long matchingStep(String secret, String code, long epochSeconds) {
        if (code == null || !code.matches("[0-9]{6}")) return -1;
        long step = epochSeconds / 30,
            matched = -1;
        for (long candidate = Math.max(0, step - 1); candidate <= step + 1; candidate++) {
            if (
                MessageDigest.isEqual(
                    code.getBytes(StandardCharsets.US_ASCII),
                    code(secret, candidate * 30, 6).getBytes(StandardCharsets.US_ASCII)
                )
            ) matched = candidate;
        }
        return matched;
    }

    @Override
    public List<String> recoveryCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            byte[] b = new byte[10];
            random.nextBytes(b);
            codes.add(HexFormat.of().formatHex(b));
        }
        return codes;
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
