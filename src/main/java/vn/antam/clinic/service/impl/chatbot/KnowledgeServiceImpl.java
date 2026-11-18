package vn.antam.clinic.service.impl.chatbot;

import static vn.antam.clinic.service.dto.chatbot.ChatTypes.*;
import static vn.antam.clinic.service.support.ClinicJdbc.*;
import static vn.antam.clinic.service.support.chatbot.KnowledgeIds.*;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.audit.AuditService;
import vn.antam.clinic.service.booking.BookingService;
import vn.antam.clinic.service.chatbot.AiConfigService;
import vn.antam.clinic.service.chatbot.KnowledgeService;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private final JdbcTemplate jdbc;
    private final AiConfigService config;
    private final AuditService audit;
    private final BookingService bookings;

    public KnowledgeServiceImpl(
        JdbcTemplate jdbc,
        AiConfigService config,
        AuditService audit,
        BookingService bookings
    ) {
        this.jdbc = jdbc;
        this.config = config;
        this.audit = audit;
        this.bookings = bookings;
    }

    @Override
    public List<PublicKnowledge> published() {
        return jdbc.query(
            "select d.id,d.slug,d.kind,v.title,v.content from knowledge_documents d join knowledge_versions v on v.id=d.published_version_id where v.status='PUBLISHED' order by d.id",
            (r, n) ->
                new PublicKnowledge(
                    r.getLong(1),
                    r.getString(2),
                    r.getString(3),
                    r.getString(4),
                    r.getString(5)
                )
        );
    }

    @Override
    public PublicKnowledge source(String slug) {
        return published()
            .stream()
            .filter(d -> d.slug().equals(slug))
            .findFirst()
            .orElseThrow(() -> missing());
    }

    @Override
    public List<KnowledgeView> list() {
        return jdbc.query(
            "select d.*,v.id version_id,v.title,v.content,v.status,v.embedding_revision,j.status job_status,j.progress,j.total,j.error from knowledge_documents d join knowledge_versions v on v.id=(select max(v2.id) from knowledge_versions v2 where v2.document_id=d.id) left join knowledge_index_jobs j on j.id=(select max(j2.id) from knowledge_index_jobs j2 where j2.version_id=v.id) order by d.id desc",
            (r, n) ->
                new KnowledgeView(
                    r.getLong("id"),
                    r.getString("slug"),
                    r.getString("kind"),
                    (Long) r.getObject("published_version_id"),
                    r.getLong("version_id"),
                    r.getString("title"),
                    r.getString("content"),
                    r.getString("status"),
                    (Long) r.getObject("embedding_revision"),
                    r.getString("job_status"),
                    r.getInt("progress"),
                    r.getInt("total"),
                    r.getString("error")
                )
        );
    }

    @Override
    public KnowledgeView get(long id) {
        return list()
            .stream()
            .filter(d -> d.id() == id)
            .findFirst()
            .orElseThrow(() -> missing());
    }

    @Override
    @Transactional
    public KnowledgeView create(DocumentInput in) {
        if (
            jdbc.queryForObject(
                "select count(*) from knowledge_documents where slug=?",
                Integer.class,
                in.slug()
            ) > 0
        ) throw conflict("Đường dẫn tài liệu đã tồn tại.");
        long id = insert(
            jdbc,
            "insert into knowledge_documents(slug,kind) values(?,?)",
            in.slug(),
            in.kind() == null ? "GUIDE" : in.kind()
        );
        insert(
            jdbc,
            "insert into knowledge_versions(document_id,title,content,created_by) values(?,?,?,?)",
            id,
            in.title().strip(),
            in.content().strip(),
            user()
        );
        audit.record("CREATE", "KNOWLEDGE", String.valueOf(id), null);
        return get(id);
    }

    @Override
    @Transactional
    public KnowledgeView edit(long id, DocumentInput in) {
        lockDocument(id);
        KnowledgeView old = get(id);
        if (
            !old.slug().equals(in.slug()) ||
            !Objects.equals(old.kind(), in.kind() == null ? "GUIDE" : in.kind())
        ) throw bad("Đường dẫn và loại tài liệu giữ cố định; tạo tài liệu mới nếu cần thay đổi.");
        insert(
            jdbc,
            "insert into knowledge_versions(document_id,title,content,created_by) values(?,?,?,?)",
            id,
            in.title().strip(),
            in.content().strip(),
            user()
        );
        audit.record("EDIT", "KNOWLEDGE", String.valueOf(id), null);
        return get(id);
    }

    @Override
    @Transactional
    public KnowledgeView enqueue(long id) {
        lockDocument(id);
        KnowledgeView d = get(id);
        var c = config.runtime();
        if (!c.embeddingReady() || c.dimensions() == null) throw conflict(
            "Kiểm tra embedding trước khi lập chỉ mục."
        );
        if (
            Set.of("QUEUED", "PROCESSING", "RETRY").contains(Objects.toString(d.jobStatus(), ""))
        ) throw conflict("Tài liệu đang được lập chỉ mục.");
        long version = d.versionId();
        if ("PUBLISHED".equals(d.status()) || "RETIRED".equals(d.status())) version = insert(
            jdbc,
            "insert into knowledge_versions(document_id,title,content,created_by) values(?,?,?,?)",
            id,
            d.title(),
            d.content(),
            user()
        );
        jdbc.update(
            "update knowledge_versions set status='INDEXING',embedding_revision=null where id=?",
            version
        );
        insert(
            jdbc,
            "insert into knowledge_index_jobs(version_id,embedding_revision) values(?,?)",
            version,
            c.embeddingRevision()
        );
        audit.record("INDEX", "KNOWLEDGE", String.valueOf(id), null);
        return get(id);
    }

    @Override
    @Transactional
    public KnowledgeView publish(long id) {
        jdbc.queryForMap("select id from chatbot_config where id=1 for update");
        lockDocument(id);
        KnowledgeView d = get(id);
        var c = config.view();
        if (
            !"READY".equals(d.status()) ||
            d.embeddingRevision() == null ||
            d.embeddingRevision() != c.embeddingRevision() ||
            !c.embeddingReady()
        ) throw conflict(
            "Cần lập chỉ mục hoàn tất bằng cấu hình embedding hiện tại trước khi xuất bản."
        );
        jdbc.update(
            "update knowledge_versions set status='RETIRED' where document_id=? and status='PUBLISHED'",
            id
        );
        jdbc.update(
            "update knowledge_versions set status='PUBLISHED',published_at=current_timestamp(6) where id=?",
            d.versionId()
        );
        jdbc.update(
            "update knowledge_documents set published_version_id=? where id=?",
            d.versionId(),
            id
        );
        audit.record("PUBLISH", "KNOWLEDGE", String.valueOf(id), null);
        return get(id);
    }

    @Override
    @Transactional
    public KnowledgeView withdraw(long id) {
        lockDocument(id);
        jdbc.update(
            "update knowledge_versions set status='RETIRED' where document_id=? and status='PUBLISHED'",
            id
        );
        jdbc.update("update knowledge_documents set published_version_id=null where id=?", id);
        audit.record("WITHDRAW", "KNOWLEDGE", String.valueOf(id), null);
        return get(id);
    }

    @Override
    @Transactional
    public Map<String, Object> seedSpecialties() {
        int count = 0;
        for (var s : bookings.specialties()) {
            String slug = "gioi-thieu-chuyen-khoa-" + s.id();
            if (
                s.description() != null &&
                !s.description().isBlank() &&
                jdbc.queryForObject(
                    "select count(*) from knowledge_documents where slug=?",
                    Integer.class,
                    slug
                ) == 0
            ) {
                create(
                    new DocumentInput(slug, "Giới thiệu " + s.name(), s.description(), "SPECIALTY")
                );
                count++;
            }
        }
        return Map.of(
            "message",
            "Đã tạo " + count + " bản nháp giới thiệu chuyên khoa. Hãy xem trước và lập chỉ mục."
        );
    }

    private void lockDocument(long id) {
        if (
            jdbc
                .query(
                    "select id from knowledge_documents where id=? for update",
                    (r, n) -> r.getLong(1),
                    id
                )
                .isEmpty()
        ) throw missing();
    }

    @Override
    public List<Long> activeVersions(long generation) {
        return jdbc.query(
            "select v.id from knowledge_documents d join knowledge_versions v on v.id=d.published_version_id where v.status='PUBLISHED' and v.embedding_revision=?",
            (r, n) -> r.getLong(1),
            generation
        );
    }

    /** Bounded lexical candidates use the same publication and generation boundary as vector search. */
    @Override
    public List<Passage> lexicalCandidates(long generation) {
        return jdbc.query(
            "select c.point_id,v.id,v.title,d.slug,c.content from knowledge_chunks c join knowledge_versions v on v.id=c.version_id join knowledge_documents d on d.published_version_id=v.id where v.status='PUBLISHED' and v.embedding_revision=? order by c.id limit 1000",
            (r, n) ->
                new Passage(
                    r.getString(1),
                    r.getLong(2),
                    r.getString(3),
                    r.getString(4),
                    r.getString(5)
                ),
            generation
        );
    }

    @Override
    public Optional<Passage> passage(String pointId, long generation) {
        return jdbc
            .query(
                "select c.point_id,v.id,v.title,d.slug,c.content from knowledge_chunks c join knowledge_versions v on v.id=c.version_id join knowledge_documents d on d.published_version_id=v.id where c.point_id=? and v.status='PUBLISHED' and v.embedding_revision=?",
                (r, n) ->
                    new Passage(
                        r.getString(1),
                        r.getLong(2),
                        r.getString(3),
                        r.getString(4),
                        r.getString(5)
                    ),
                pointId,
                generation
            )
            .stream()
            .findFirst();
    }
}
