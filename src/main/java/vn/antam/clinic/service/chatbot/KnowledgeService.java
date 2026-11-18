package vn.antam.clinic.service.chatbot;

import static vn.antam.clinic.service.dto.chatbot.ChatTypes.*;

import java.util.*;

/** Business contract; implemented by the Spring-managed KnowledgeServiceImpl. */
public interface KnowledgeService {
    public List<PublicKnowledge> published();

    public PublicKnowledge source(String slug);

    public List<KnowledgeView> list();

    public KnowledgeView get(long id);

    public KnowledgeView create(DocumentInput in);

    public KnowledgeView edit(long id, DocumentInput in);

    public KnowledgeView enqueue(long id);

    public KnowledgeView publish(long id);

    public KnowledgeView withdraw(long id);

    public Map<String, Object> seedSpecialties();

    public List<Long> activeVersions(long generation);

    public record Passage(
        String pointId,
        long versionId,
        String title,
        String slug,
        String content
    ) {}

    public List<Passage> lexicalCandidates(long generation);

    public Optional<Passage> passage(String pointId, long generation);
}
