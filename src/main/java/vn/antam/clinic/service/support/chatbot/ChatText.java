package vn.antam.clinic.service.support.chatbot;

import static vn.antam.clinic.service.dto.chatbot.ChatTypes.*;

import java.time.*;
import java.util.*;

public final class ChatText {

    private ChatText() {}

    public static String redact(String text) {
        return text
            .replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[email]")
            .replaceAll("(?<!\\d)(?:\\+84|0)[ .-]?(?:\\d[ .-]?){8,10}(?!\\d)", "[điện thoại]")
            .replaceAll("(?<!\\d)\\d{9,12}(?!\\d)", "[mã cá nhân]");
    }
}
