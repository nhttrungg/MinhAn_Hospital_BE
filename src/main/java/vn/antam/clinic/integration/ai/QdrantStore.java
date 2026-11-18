package vn.antam.clinic.integration.ai;

import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QdrantStore {

    public record Point(String id, long versionId, List<Double> vector) {}

    public record Hit(String pointId, double score) {}

    private final AiHttpClient http;
    private final String base, key;

    public QdrantStore(
        AiHttpClient http,
        @Value("${app.chatbot.qdrant-url:http://127.0.0.1:6333}") String base,
        @Value("${app.chatbot.qdrant-key:}") String key
    ) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
        this.key = key;
    }

    public static String collection(long generation) {
        return "antam_knowledge_g" + generation;
    }

    private com.fasterxml.jackson.databind.JsonNode call(String path, String method, Object body) {
        return http.request(base + path, key, method, body, Duration.ofSeconds(8), true);
    }

    public void ensure(long generation, int dimensions) {
        String path = "/collections/" + collection(generation);
        var listed = call("/collections", "GET", null).path("result").path("collections");
        boolean exists = false;
        for (var c : listed)
            if (collection(generation).equals(c.path("name").asText())) exists = true;
        if (!exists) call(
            path,
            "PUT",
            Map.of("vectors", Map.of("size", dimensions, "distance", "Cosine"))
        );
        else if (
            call(path, "GET", null)
                .path("result")
                .path("config")
                .path("params")
                .path("vectors")
                .path("size")
                .asInt() != dimensions
        ) throw new AiHttpClient.RemoteFailure(
            "Collection Qdrant có số chiều khác cấu hình.",
            false
        );
        call(path + "/index", "PUT", Map.of("field_name", "versionId", "field_schema", "integer"));
    }

    public void upsert(long generation, List<Point> points) {
        call(
            "/collections/" + collection(generation) + "/points?wait=true",
            "PUT",
            Map.of(
                "points",
                points
                    .stream()
                    .map(p ->
                        Map.of(
                            "id",
                            p.id(),
                            "vector",
                            p.vector(),
                            "payload",
                            Map.of("versionId", p.versionId())
                        )
                    )
                    .toList()
            )
        );
    }

    public List<Hit> search(long generation, List<Double> vector, List<Long> published) {
        if (published.isEmpty()) return List.of();
        var filter = Map.of(
            "must",
            List.of(Map.of("key", "versionId", "match", Map.of("any", published)))
        );
        var result = call(
            "/collections/" + collection(generation) + "/points/query",
            "POST",
            Map.of("query", vector, "filter", filter, "limit", 15, "with_payload", false)
        )
            .path("result")
            .path("points");
        if (!result.isArray()) throw new AiHttpClient.RemoteFailure(
            "Qdrant không trả kết quả tìm kiếm hợp lệ.",
            false
        );
        List<Hit> hits = new ArrayList<>();
        for (var h : result) hits.add(new Hit(h.path("id").asText(), h.path("score").asDouble()));
        return hits;
    }

    public void activate(long generation) {
        var aliases = call("/aliases", "GET", null).path("result").path("aliases");
        boolean exists = false;
        for (var a : aliases)
            if ("antam_knowledge_current".equals(a.path("alias_name").asText())) exists = true;
        List<Object> actions = new ArrayList<>();
        if (exists) actions.add(
            Map.of("delete_alias", Map.of("alias_name", "antam_knowledge_current"))
        );
        actions.add(
            Map.of(
                "create_alias",
                Map.of(
                    "collection_name",
                    collection(generation),
                    "alias_name",
                    "antam_knowledge_current"
                )
            )
        );
        call("/collections/aliases", "POST", Map.of("actions", actions));
    }
}
