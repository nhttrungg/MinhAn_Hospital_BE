package vn.antam.clinic.service.chatbot;

import static vn.antam.clinic.service.dto.chatbot.ChatTypes.*;

import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;

/** Business contract; implemented by the Spring-managed ChatSessionStoreImpl. */
public interface ChatSessionStore {
    public static final String ATTRIBUTE = "antam.chat.session";

    interface LockHandle extends AutoCloseable {
        @Override
        void close();
    }

    public LockHandle lock(HttpSession session);

    public String ensure(HttpSession session);

    public Optional<Reply> duplicate(String id, MessageRequest request);

    public void quota(String id, String ip);

    public List<Map<String, Object>> history(String id);

    public Map<String, String> lookupContext(String id);

    public com.fasterxml.jackson.databind.node.ObjectNode mergeLookupContext(
        String id,
        com.fasterxml.jackson.databind.node.ObjectNode current
    );

    public void rememberLookupContext(
        String id,
        com.fasterxml.jackson.databind.node.ObjectNode filters
    );

    public void save(String id, MessageRequest request, String sanitized, Reply reply);

    public void reset(HttpSession session);

    public void clean();
}
