package vn.antam.clinic.service.chatbot;

import java.util.*;

/** Business contract; implemented by the Spring-managed KnowledgeRetrievalImpl. */
public interface KnowledgeRetrieval {
    public record Match(KnowledgeService.Passage passage, double score) {}

    public List<Match> search(AiConfigService.Runtime config, String question);
}
