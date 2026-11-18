package vn.antam.clinic.service.dto.chatbot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ChatTypes {

    private ChatTypes() {}

    public record MessageRequest(
        @NotBlank @Size(max = 2000) String message,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String clientMessageId
    ) {}

    public record RetrievalRequest(@NotBlank @Size(max = 2000) String message) {}

    public record Source(String id, String title, String url) {}

    public record Action(String label, String url, String detail) {}

    public record Reply(
        String messageId,
        String answer,
        List<Source> sources,
        List<Action> actions,
        String status,
        boolean demoData
    ) {}

    public record DocumentInput(
        @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 180) String slug,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200000) String content,
        @Pattern(regexp = "FAQ|GUIDE|SPECIALTY") String kind
    ) {}

    public record ConfigInput(
        @NotBlank @Size(max = 500) String chatBaseUrl,
        @Size(max = 500) String chatKey,
        @NotBlank @Size(max = 200) String chatModel,
        boolean embeddingShareChat,
        @Size(max = 500) String embeddingBaseUrl,
        @Size(max = 500) String embeddingKey,
        @Size(max = 200) String embeddingModel,
        Boolean clearChatKey,
        Boolean clearEmbeddingKey
    ) {}

    public record ConfigView(
        boolean enabled,
        String chatBaseUrl,
        boolean chatKeySet,
        String chatModel,
        boolean embeddingShareChat,
        String embeddingBaseUrl,
        boolean embeddingKeySet,
        String embeddingModel,
        long revision,
        long embeddingRevision,
        boolean chatReady,
        boolean embeddingReady,
        Integer dimensions,
        boolean masterKeyReady,
        int indexedPublishedDocuments
    ) {}

    public record KnowledgeView(
        long id,
        String slug,
        String kind,
        Long publishedVersionId,
        long versionId,
        String title,
        String content,
        String status,
        Long embeddingRevision,
        String jobStatus,
        int progress,
        int total,
        String error
    ) {}

    public record PublicKnowledge(
        long id,
        String slug,
        String kind,
        String title,
        String content
    ) {}
}
