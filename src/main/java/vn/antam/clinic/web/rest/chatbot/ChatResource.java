package vn.antam.clinic.web.rest.chatbot;

import static vn.antam.clinic.service.dto.chatbot.ChatTypes.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.antam.clinic.service.chatbot.AiConfigService;
import vn.antam.clinic.service.chatbot.ChatService;
import vn.antam.clinic.service.chatbot.ChatSessionStore;
import vn.antam.clinic.service.chatbot.KnowledgeService;

@RestController
@RequestMapping("/api/v1")
public class ChatResource {

    private final ChatService chat;
    private final ChatSessionStore sessions;
    private final AiConfigService config;
    private final KnowledgeService knowledge;

    public ChatResource(
        ChatService chat,
        ChatSessionStore sessions,
        AiConfigService config,
        KnowledgeService knowledge
    ) {
        this.chat = chat;
        this.sessions = sessions;
        this.config = config;
        this.knowledge = knowledge;
    }

    @PostMapping("/public/chat/messages")
    public Reply message(@Valid @RequestBody MessageRequest body, HttpServletRequest request) {
        return chat.send(body, request.getSession(), request.getRemoteAddr());
    }

    @DeleteMapping("/public/chat/session")
    public ResponseEntity<Void> reset(HttpServletRequest request) {
        sessions.reset(request.getSession(false));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public/knowledge")
    public List<PublicKnowledge> publicList() {
        return knowledge.published();
    }

    @GetMapping("/public/knowledge/{slug}")
    public PublicKnowledge source(@PathVariable String slug) {
        return knowledge.source(slug);
    }

    @GetMapping("/admin/chatbot/config")
    public ConfigView config() {
        return config.view();
    }

    @PutMapping("/admin/chatbot/config")
    public ConfigView configure(@Valid @RequestBody ConfigInput body) {
        return config.save(body);
    }

    @PostMapping("/admin/chatbot/test-chat")
    public Map<String, Object> testChat() {
        return config.testChat();
    }

    @PostMapping("/admin/chatbot/test-embedding")
    public Map<String, Object> testEmbedding() {
        return config.testEmbedding();
    }

    @GetMapping("/admin/chatbot/models")
    public List<String> models(@RequestParam(defaultValue = "false") boolean embedding) {
        return config.models(embedding);
    }

    @PostMapping("/admin/chatbot/retrieve")
    public List<ChatService.Retrieved> retrieve(@Valid @RequestBody RetrievalRequest body) {
        return chat.retrieve(body.message());
    }

    @PostMapping("/admin/chatbot/enabled")
    public ConfigView enabled(@RequestBody Map<String, Boolean> body) {
        return config.enabled(Boolean.TRUE.equals(body.get("enabled")));
    }

    @GetMapping("/admin/knowledge")
    public List<KnowledgeView> list() {
        return knowledge.list();
    }

    @PostMapping("/admin/knowledge")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeView create(@Valid @RequestBody DocumentInput body) {
        return knowledge.create(body);
    }

    @PutMapping("/admin/knowledge/{id}")
    public KnowledgeView edit(@PathVariable long id, @Valid @RequestBody DocumentInput body) {
        return knowledge.edit(id, body);
    }

    @PostMapping("/admin/knowledge/{id}/index")
    public KnowledgeView index(@PathVariable long id) {
        return knowledge.enqueue(id);
    }

    @PostMapping("/admin/knowledge/{id}/publish")
    public KnowledgeView publish(@PathVariable long id) {
        return knowledge.publish(id);
    }

    @PostMapping("/admin/knowledge/{id}/withdraw")
    public KnowledgeView withdraw(@PathVariable long id) {
        return knowledge.withdraw(id);
    }

    @PostMapping("/admin/knowledge/seed-specialties")
    public Map<String, Object> seed() {
        return knowledge.seedSpecialties();
    }
}
