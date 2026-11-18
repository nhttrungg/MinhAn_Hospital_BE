package vn.antam.clinic.service.impl.chatbot;

import static vn.antam.clinic.service.dto.chatbot.ChatTypes.*;
import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.integration.ai.AiHttpClient;
import vn.antam.clinic.integration.ai.AiSecretCipher;
import vn.antam.clinic.integration.ai.QdrantStore;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.chatbot.AiConfigService;

@Service
public class AiConfigServiceImpl implements AiConfigService {

    private final JdbcTemplate jdbc;
    private final AiSecretCipher cipher;
    private final AiHttpClient ai;
    private final QdrantStore vector;
    private final AuditService audit;
    private final String defaultBase;

    public AiConfigServiceImpl(
        JdbcTemplate jdbc,
        AiSecretCipher cipher,
        AiHttpClient ai,
        QdrantStore vector,
        AuditService audit,
        @Value("${app.chatbot.default-base-url:http://127.0.0.1:20128/v1}") String defaultBase
    ) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.ai = ai;
        this.vector = vector;
        this.audit = audit;
        this.defaultBase = defaultBase;
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("select * from chatbot_config where id=1");
    }

    private static long number(Map<String, Object> r, String k) {
        return ((Number) r.get(k)).longValue();
    }

    private static boolean flag(Map<String, Object> r, String k) {
        return number(r, k) != 0;
    }

    private static boolean tested(Map<String, Object> r, String k, long v) {
        return r.get(k) != null && number(r, k) == v;
    }

    private String configuredBase(Map<String, Object> r, String key) {
        String b = (String) r.get(key);
        return "http://127.0.0.1:20128/v1".equals(b) && number(r, "revision") == 1
            ? AiHttpClient.base(defaultBase)
            : b;
    }

    @Override
    public Runtime runtime() {
        var r = row();
        boolean share = flag(r, "embedding_share_chat");
        long rev = number(r, "revision"),
            emb = number(r, "embedding_revision");
        return new Runtime(
            flag(r, "enabled"),
            configuredBase(r, "chat_base_url"),
            cipher.decrypt((byte[]) r.get("chat_key")),
            (String) r.get("chat_model"),
            configuredBase(r, share ? "chat_base_url" : "embedding_base_url"),
            cipher.decrypt((byte[]) r.get(share ? "chat_key" : "embedding_key")),
            (String) r.get("embedding_model"),
            rev,
            emb,
            tested(r, "chat_tested_revision", rev),
            tested(r, "embedding_tested_revision", emb),
            r.get("dimensions") == null ? null : ((Number) r.get("dimensions")).intValue()
        );
    }

    @Override
    public ConfigView view() {
        var r = row();
        long rev = number(r, "revision"),
            emb = number(r, "embedding_revision");
        int ready = jdbc.queryForObject(
            "select count(*) from knowledge_documents d join knowledge_versions v on v.id=d.published_version_id where v.embedding_revision=? and v.status='PUBLISHED'",
            Integer.class,
            emb
        );
        return new ConfigView(
            flag(r, "enabled"),
            configuredBase(r, "chat_base_url"),
            r.get("chat_key") != null,
            (String) r.get("chat_model"),
            flag(r, "embedding_share_chat"),
            configuredBase(r, "embedding_base_url"),
            r.get("embedding_key") != null,
            (String) r.get("embedding_model"),
            rev,
            emb,
            tested(r, "chat_tested_revision", rev),
            tested(r, "embedding_tested_revision", emb),
            r.get("dimensions") == null ? null : ((Number) r.get("dimensions")).intValue(),
            cipher.ready(),
            ready
        );
    }

    @Override
    @Transactional
    public ConfigView save(ConfigInput input) {
        var r = jdbc.queryForMap("select * from chatbot_config where id=1 for update");
        String base = AiHttpClient.base(input.chatBaseUrl());
        String eb = input.embeddingShareChat() ? base : AiHttpClient.base(input.embeddingBaseUrl());
        String embeddingModel =
            input.embeddingModel() == null ? "" : input.embeddingModel().strip();
        byte[] ck = Boolean.TRUE.equals(input.clearChatKey()) ? null : (byte[]) r.get("chat_key"),
            ek = Boolean.TRUE.equals(input.clearEmbeddingKey())
                ? null
                : (byte[]) r.get("embedding_key");
        if (input.chatKey() != null && !input.chatKey().isBlank()) ck = cipher.encrypt(
            input.chatKey().strip()
        );
        if (input.embeddingKey() != null && !input.embeddingKey().isBlank()) ek = cipher.encrypt(
            input.embeddingKey().strip()
        );
        boolean oldShare = flag(r, "embedding_share_chat");
        boolean embChanged =
            oldShare != input.embeddingShareChat() ||
            !Objects.equals(r.get("embedding_model"), embeddingModel) ||
            !Objects.equals(r.get(oldShare ? "chat_base_url" : "embedding_base_url"), eb) ||
            !Arrays.equals(
                (byte[]) r.get(oldShare ? "chat_key" : "embedding_key"),
                input.embeddingShareChat() ? ck : ek
            );
        jdbc.update(
            "update chatbot_config set enabled=0,chat_base_url=?,chat_key=?,chat_model=?,embedding_share_chat=?,embedding_base_url=?,embedding_key=?,embedding_model=?,revision=revision+1,embedding_revision=embedding_revision+?,chat_tested_revision=null,embedding_tested_revision=case when ? then null else embedding_tested_revision end,dimensions=case when ? then null else dimensions end,updated_at=current_timestamp(6) where id=1",
            base,
            ck,
            input.chatModel().strip(),
            input.embeddingShareChat(),
            eb,
            ek,
            embeddingModel,
            embChanged ? 1 : 0,
            embChanged,
            embChanged
        );
        audit.record("CONFIGURE", "CHATBOT", "1", null);
        return view();
    }

    @Override
    public Map<String, Object> testChat() {
        Runtime c = runtime();
        var tool = Map.<String, Object>of(
            "type",
            "function",
            "function",
            Map.of(
                "name",
                "ping",
                "description",
                "Kiểm tra công cụ chỉ đọc.",
                "parameters",
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)
            )
        );
        var answer = ai.chat(
            c,
            List.of(Map.of("role", "user", "content", "Gọi công cụ ping để kiểm tra kết nối.")),
            List.of(tool),
            Map.of("type", "function", "function", Map.of("name", "ping"))
        );
        var calls = answer.path("tool_calls");
        boolean ok = false;
        if (calls.isArray()) for (var call : calls)
            if ("ping".equals(call.path("function").path("name").asText())) ok = true;
        if (!ok) throw bad("Model không gọi được công cụ chỉ đọc. Chọn model hỗ trợ tool calling.");
        if (
            jdbc.update(
                "update chatbot_config set chat_tested_revision=? where id=1 and revision=?",
                c.revision(),
                c.revision()
            ) != 1
        ) throw conflict("Cấu hình đã đổi trong lúc kiểm tra.");
        return Map.of("message", "Kết nối chat và công cụ chỉ đọc hoạt động.");
    }

    @Override
    public Map<String, Object> testEmbedding() {
        Runtime c = runtime();
        if (c.embeddingModel().isBlank()) throw bad(
            "Chưa chọn model embedding. Cấu hình kết nối embedding trước khi lập chỉ mục RAG."
        );
        Runtime probe = new Runtime(
            c.enabled(),
            c.chatBase(),
            c.chatKey(),
            c.chatModel(),
            c.embeddingBase(),
            c.embeddingKey(),
            c.embeddingModel(),
            c.revision(),
            c.embeddingRevision(),
            c.chatReady(),
            c.embeddingReady(),
            null
        );
        int dimensions = ai
            .embed(probe, List.of("Hướng dẫn đặt lịch khám tại Minh An"))
            .getFirst()
            .size();
        if (c.dimensions() != null && c.dimensions() != dimensions) {
            jdbc.update(
                "update chatbot_config set enabled=0,embedding_revision=embedding_revision+1,embedding_tested_revision=null,dimensions=null where id=1 and revision=?",
                c.revision()
            );
            throw conflict(
                "Số chiều đã thay đổi. Kiểm tra embedding lại và lập chỉ mục lại tài liệu."
            );
        }
        vector.ensure(c.embeddingRevision(), dimensions);
        if (
            jdbc.update(
                "update chatbot_config set dimensions=?,embedding_tested_revision=? where id=1 and revision=? and embedding_revision=?",
                dimensions,
                c.embeddingRevision(),
                c.revision(),
                c.embeddingRevision()
            ) != 1
        ) throw conflict("Cấu hình đã đổi trong lúc kiểm tra.");
        return Map.of("message", "Embedding và Qdrant hoạt động.", "dimensions", dimensions);
    }

    @Override
    public List<String> models(boolean embedding) {
        Runtime c = runtime();
        return ai.models(
            embedding ? c.embeddingBase() : c.chatBase(),
            embedding ? c.embeddingKey() : c.chatKey()
        );
    }

    @Override
    @Transactional
    public ConfigView enabled(boolean enabled) {
        jdbc.queryForMap("select id from chatbot_config where id=1 for update");
        ConfigView v = view();
        if (enabled) {
            if (
                !v.chatReady() || !v.embeddingReady() || v.indexedPublishedDocuments() == 0
            ) throw conflict(
                "Cần kiểm tra chat, embedding và có ít nhất một tài liệu xuất bản đã lập chỉ mục đúng cấu hình."
            );
            runtime(); // Validate that the current master key can decrypt saved credentials.
            vector.activate(v.embeddingRevision());
        }
        jdbc.update("update chatbot_config set enabled=? where id=1", enabled);
        audit.record(enabled ? "ENABLE" : "DISABLE", "CHATBOT", "1", null);
        return view();
    }
}
