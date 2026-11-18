package vn.antam.clinic.service.chatbot;

import static vn.antam.clinic.service.dto.chatbot.ChatTypes.*;

import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;

/** Business contract; implemented by the Spring-managed ChatServiceImpl. */
public interface ChatService {
    public Reply send(MessageRequest request, HttpSession session, String ip);

    public record Retrieved(String pointId, String title, String url, double score) {}

    public List<Retrieved> retrieve(String question);
}
