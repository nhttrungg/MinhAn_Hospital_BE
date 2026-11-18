package vn.antam.clinic.service.impl.chatbot;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.antam.clinic.integration.ai.AiHttpClient;
import vn.antam.clinic.integration.ai.QdrantStore;
import vn.antam.clinic.service.chatbot.AiConfigService;
import vn.antam.clinic.service.chatbot.KnowledgeRetrieval;
import vn.antam.clinic.service.chatbot.KnowledgeService;
import vn.antam.clinic.service.support.chatbot.PublicLookupTools;

/** Reciprocal rank fusion keeps accented semantic matches and accent-free lexical matches. */
@Service
public class KnowledgeRetrievalImpl implements KnowledgeRetrieval {

    private final AiHttpClient ai;
    private final QdrantStore vector;
    private final KnowledgeService knowledge;
    private final double floor;
    private static final Set<String> STOP = Set.of(
        "toi",
        "minh",
        "ban",
        "cho",
        "co",
        "the",
        "khong",
        "thi",
        "la",
        "va",
        "hoac",
        "cua",
        "nao",
        "gi",
        "dau",
        "nhu",
        "sao",
        "de",
        "duoc",
        "mot",
        "nhung",
        "cac",
        "nay",
        "do",
        "muon",
        "can",
        "nen",
        "truoc",
        "roi",
        "phai",
        "khi",
        "voi"
    );

    public KnowledgeRetrievalImpl(
        AiHttpClient ai,
        QdrantStore vector,
        KnowledgeService knowledge,
        @Value("${app.chatbot.score-floor:0.35}") double floor
    ) {
        this.ai = ai;
        this.vector = vector;
        this.knowledge = knowledge;
        this.floor = floor;
    }

    private static Set<String> terms(String text) {
        Set<String> result = new LinkedHashSet<>();
        for (String term : PublicLookupTools.normalize(text).split("[^a-z0-9]+"))
            if (term.length() > 1 && !STOP.contains(term)) result.add(term);
        return result;
    }

    static double lexicalScore(
        String question,
        KnowledgeService.Passage passage,
        List<KnowledgeService.Passage> corpus
    ) {
        return lexicalScore(terms(question), passage, frequencies(corpus), corpus.size());
    }

    private static Map<String, Integer> frequencies(List<KnowledgeService.Passage> corpus) {
        Map<String, Integer> counts = new HashMap<>();
        for (var passage : corpus)
            for (String term : terms(passage.content())) counts.merge(term, 1, Integer::sum);
        return counts;
    }

    private static double lexicalScore(
        Set<String> query,
        KnowledgeService.Passage passage,
        Map<String, Integer> counts,
        int total
    ) {
        if (query.size() < 2) return 0;
        var content = terms(passage.content());
        var title = terms(passage.title());
        long overlap = query.stream().filter(content::contains).count();
        if (overlap < 2 || overlap < (query.size() + 1) / 2) return 0;
        double score = 0;
        for (String term : query)
            if (content.contains(term)) {
                double idf = Math.log(1 + (total + 1d) / (counts.getOrDefault(term, 0) + 1d));
                score += idf * (title.contains(term) ? 2 : 1);
            }
        return score;
    }

    @Override
    public List<Match> search(AiConfigService.Runtime config, String question) {
        var active = knowledge.activeVersions(config.embeddingRevision());
        if (active.isEmpty()) return List.of();
        var embedding = ai.embed(config, List.of(question)).getFirst();
        var semantic = vector
            .search(config.embeddingRevision(), embedding, active)
            .stream()
            .filter(h -> h.score() >= floor)
            .toList();
        var corpus = knowledge.lexicalCandidates(config.embeddingRevision());
        var counts = frequencies(corpus);
        var query = terms(question);
        var lexical = corpus
            .stream()
            .map(p -> new Match(p, lexicalScore(query, p, counts, corpus.size())))
            .filter(m -> m.score() > 0)
            .sorted(Comparator.comparingDouble(Match::score).reversed())
            .limit(15)
            .toList();
        Map<String, Double> scores = new HashMap<>();
        Map<String, KnowledgeService.Passage> passages = new HashMap<>();
        for (int i = 0; i < semantic.size(); i++) {
            var hit = semantic.get(i);
            var passage = knowledge.passage(hit.pointId(), config.embeddingRevision());
            if (passage.isPresent()) {
                passages.put(hit.pointId(), passage.get());
                scores.merge(hit.pointId(), 1d / (20 + i + 1), Double::sum);
            }
        }
        for (int i = 0; i < lexical.size(); i++) {
            var passage = lexical.get(i).passage();
            passages.put(passage.pointId(), passage);
            var title = terms(passage.title());
            long titleMatches = query.stream().filter(title::contains).count();
            // A near-complete title match should survive generic semantic hits sharing common words.
            double weight =
                query.size() >= 3 && titleMatches >= Math.ceil(query.size() * .8) ? 2 : 1;
            scores.merge(passage.pointId(), weight / (20 + i + 1), Double::sum);
        }
        return scores
            .entrySet()
            .stream()
            .sorted(
                Map.Entry.<String, Double>comparingByValue()
                    .reversed()
                    .thenComparing(Map.Entry::getKey)
            )
            .limit(5)
            .map(e -> new Match(passages.get(e.getKey()), e.getValue()))
            .filter(m ->
                knowledge.passage(m.passage().pointId(), config.embeddingRevision()).isPresent()
            )
            .toList();
    }
}
