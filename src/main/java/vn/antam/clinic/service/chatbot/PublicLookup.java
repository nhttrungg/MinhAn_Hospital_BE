package vn.antam.clinic.service.chatbot;

import static vn.antam.clinic.service.dto.chatbot.ChatTypes.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;

/** Business contract; implemented by the Spring-managed PublicLookupImpl. */
public interface PublicLookup {
    public record Result(String answer, List<Source> sources, List<Action> actions) {}

    public boolean demo();

    public List<Map<String, String>> careCatalog();

    public Result execute(JsonNode args);
}
