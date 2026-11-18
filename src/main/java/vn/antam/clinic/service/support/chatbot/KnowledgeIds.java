package vn.antam.clinic.service.support.chatbot;

import static vn.antam.clinic.service.dto.chatbot.ChatTypes.*;
import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class KnowledgeIds {

    private KnowledgeIds() {}

    public static String pointId(long version, int ordinal) {
        return UUID.nameUUIDFromBytes(
            ("antam:" + version + ":" + ordinal).getBytes(StandardCharsets.UTF_8)
        ).toString();
    }
}
