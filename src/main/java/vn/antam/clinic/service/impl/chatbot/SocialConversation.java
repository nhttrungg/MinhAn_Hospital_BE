package vn.antam.clinic.service.impl.chatbot;

import java.util.Optional;
import java.util.regex.Pattern;
import vn.antam.clinic.service.support.chatbot.PublicLookupTools;

/** Whole-utterance courtesy replies contain no clinic facts and need no retrieval. */
final class SocialConversation {

    private static final String PARTICLE =
        "(?:ban|anh|chi|em|nhe|nha|nhi|a|ah|ha|nhe ban|rat nhieu|nhieu|nhieu lam|an tam)";
    private static final Pattern SOCIAL = Pattern.compile(
        "(?:(cam on|thanks|thank you|thank u|xin chao|chao|hello|hi|tam biet|chao tam biet|hen gap lai|bye|goodbye|ok|okay|oke|oki|vang|da|uh|u|duoc roi|hieu roi)(?: " +
            PARTICLE +
            ")*(?: |$))+"
    );

    private SocialConversation() {}

    static Optional<String> answer(String question) {
        String text = PublicLookupTools.normalize(question)
            .replaceAll("[^a-z0-9]+", " ")
            .strip()
            .replaceAll(" +", " ");
        if (!SOCIAL.matcher(text).matches()) return Optional.empty();
        if (text.matches(".*\\b(tam biet|hen gap lai|bye|goodbye)\\b.*")) return forIntent(
            "FAREWELL"
        );
        if (text.matches(".*\\b(cam on|thanks|thank you|thank u)\\b.*")) return forIntent("THANKS");
        if (text.matches(".*\\b(chao|hello|hi)\\b.*")) return forIntent("GREETING");
        return forIntent("ACKNOWLEDGEMENT");
    }

    static Optional<String> forIntent(String intent) {
        return switch (intent) {
            case "THANKS" -> Optional.of(
                "Không có gì bạn nhé! Nếu cần thêm thông tin hoặc hỗ trợ đặt khám, bạn cứ nhắn tôi."
            );
            case "GREETING" -> Optional.of(
                "Chào bạn! Tôi là trợ lý Minh An. Bạn muốn tìm bác sĩ, xem giá và lịch khám, hay cần hướng dẫn đặt lịch?"
            );
            case "FAREWELL" -> Optional.of(
                "Chào bạn, hẹn gặp lại! Khi cần hỗ trợ, bạn cứ nhắn Minh An nhé."
            );
            case "ACKNOWLEDGEMENT" -> Optional.of(
                "Vâng, bạn nhé. Nếu còn điều gì cần hỗ trợ, bạn cứ nhắn tôi."
            );
            default -> Optional.empty();
        };
    }
}
