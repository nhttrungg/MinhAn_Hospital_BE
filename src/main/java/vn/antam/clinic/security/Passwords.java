package vn.antam.clinic.security;

import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import vn.antam.clinic.web.rest.errors.ApiException;

public final class Passwords {

    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final Pattern DIGIT = Pattern.compile("\\p{N}");

    private Passwords() {}

    public static void check(String password) {
        if (
            password == null ||
            password.length() < 8 ||
            password.length() > 72 ||
            !LETTER.matcher(password).find() ||
            !DIGIT.matcher(password).find()
        ) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Mật khẩu chưa đạt yêu cầu",
                "Mật khẩu cần từ 8 đến 72 ký tự, có ít nhất một chữ và một chữ số."
            );
        }
    }
}
