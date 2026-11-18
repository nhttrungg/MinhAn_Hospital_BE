package vn.antam.clinic.integration.ai;

import static vn.antam.clinic.service.support.ClinicJdbc.bad;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-GCM envelope adapted from the previous project's AiSecretCipher. */
@Component
public class AiSecretCipher {

    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public AiSecretCipher(@Value("${app.chatbot.master-key:}") String master) {
        key = master.isBlank() ? null : derive(master);
    }

    public boolean ready() {
        return key != null;
    }

    public byte[] encrypt(String plain) {
        if (!ready()) throw bad("Cần cấu hình APP_AI_CONFIG_MASTER_KEY trước khi lưu khóa API.");
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, iv)
            );
            byte[] encrypted = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] result = Arrays.copyOf(iv, iv.length + encrypted.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return result;
        } catch (GeneralSecurityException e) {
            throw bad("Không mã hóa được khóa API.");
        }
    }

    public String decrypt(byte[] packed) {
        if (packed == null) return "";
        if (!ready()) throw bad("Thiếu APP_AI_CONFIG_MASTER_KEY để đọc cấu hình AI.");
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, Arrays.copyOf(packed, 12))
            );
            return new String(c.doFinal(packed, 12, packed.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw bad("Không giải mã được khóa API. Kiểm tra khóa chủ.");
        }
    }

    private static byte[] derive(String master) {
        try {
            byte[] b = Base64.getDecoder().decode(master);
            if (b.length == 16 || b.length == 24 || b.length == 32) return b;
        } catch (IllegalArgumentException ignored) {}
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                master.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
