package vn.antam.clinic.service.chatbot;

import static vn.antam.clinic.service.dto.chatbot.ChatTypes.*;

import java.util.*;

/** Business contract; implemented by the Spring-managed AiConfigServiceImpl. */
public interface AiConfigService {
    public record Runtime(
        boolean enabled,
        String chatBase,
        String chatKey,
        String chatModel,
        String embeddingBase,
        String embeddingKey,
        String embeddingModel,
        long revision,
        long embeddingRevision,
        boolean chatReady,
        boolean embeddingReady,
        Integer dimensions
    ) {}

    public Runtime runtime();

    public ConfigView view();

    public ConfigView save(ConfigInput input);

    public Map<String, Object> testChat();

    public Map<String, Object> testEmbedding();

    public List<String> models(boolean embedding);

    public ConfigView enabled(boolean enabled);
}
