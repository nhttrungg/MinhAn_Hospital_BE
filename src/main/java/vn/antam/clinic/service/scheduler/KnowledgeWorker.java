package vn.antam.clinic.service.scheduler;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.antam.clinic.integration.ai.AiHttpClient;
import vn.antam.clinic.integration.ai.QdrantStore;
import vn.antam.clinic.service.chatbot.AiConfigService;
import vn.antam.clinic.service.support.chatbot.KnowledgeIds;
import vn.antam.clinic.service.support.chatbot.TextChunker;
import vn.antam.clinic.web.rest.errors.ApiException;

@Component
public class KnowledgeWorker {

    private final JdbcTemplate jdbc;
    private final AiConfigService config;
    private final AiHttpClient ai;
    private final QdrantStore vector;
    private final TransactionTemplate tx;

    public KnowledgeWorker(
        JdbcTemplate jdbc,
        AiConfigService config,
        AiHttpClient ai,
        QdrantStore vector,
        PlatformTransactionManager tm
    ) {
        this.jdbc = jdbc;
        this.config = config;
        this.ai = ai;
        this.vector = vector;
        this.tx = new TransactionTemplate(tm);
    }

    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(
        name = "knowledge-index",
        lockAtMostFor = "30m",
        lockAtLeastFor = "2s"
    )
    @Scheduled(
        fixedDelayString = "${app.chatbot.worker-delay-ms:3000}",
        initialDelayString = "${app.chatbot.worker-initial-delay-ms:5000}"
    )
    public void tick() {
        Long job = tx.execute(status -> {
            var ids = jdbc.query(
                "select id from knowledge_index_jobs where ((status in ('QUEUED','RETRY') and available_at<=current_timestamp(6)) or (status='PROCESSING' and lease_until<current_timestamp(6))) order by id limit 1 for update skip locked",
                (r, n) -> r.getLong(1)
            );
            if (ids.isEmpty()) return null;
            long id = ids.getFirst();
            jdbc.update(
                "update knowledge_index_jobs set status='PROCESSING',attempts=attempts+1,lease_until=date_add(current_timestamp(6),interval 5 minute),error=null where id=?",
                id
            );
            return id;
        });
        if (job == null) return;
        try {
            run(job);
        } catch (RuntimeException e) {
            int attempts = jdbc.queryForObject(
                "select attempts from knowledge_index_jobs where id=?",
                Integer.class,
                job
            );
            boolean retry =
                e instanceof AiHttpClient.RemoteFailure f && f.retryable && attempts < 3;
            String error =
                e instanceof AiHttpClient.RemoteFailure || e instanceof ApiException
                    ? e.getMessage()
                    : "Không xử lý được chỉ mục. Kiểm tra cấu hình và thử lại.";
            jdbc.update(
                "update knowledge_index_jobs set status=?,error=?,available_at=date_add(current_timestamp(6),interval 30 second),lease_until=null where id=?",
                retry ? "RETRY" : "FAILED",
                error,
                job
            );
            if (!retry) jdbc.update(
                "update knowledge_versions set status='FAILED' where id=(select version_id from knowledge_index_jobs where id=?)",
                job
            );
        }
    }

    private void run(long job) {
        var r = jdbc.queryForMap(
            "select j.version_id,j.embedding_revision,v.title,v.content from knowledge_index_jobs j join knowledge_versions v on v.id=j.version_id where j.id=?",
            job
        );
        long version = ((Number) r.get("version_id")).longValue(),
            generation = ((Number) r.get("embedding_revision")).longValue();
        var c = config.runtime();
        if (
            !c.embeddingReady() || c.embeddingRevision() != generation
        ) throw new AiHttpClient.RemoteFailure(
            "Cấu hình embedding đã thay đổi. Lập chỉ mục lại bằng cấu hình mới.",
            false
        );
        var texts = TextChunker.split((String) r.get("title"), (String) r.get("content"));
        if (texts.isEmpty() || texts.size() > 1000) throw new AiHttpClient.RemoteFailure(
            "Số đoạn tài liệu không hợp lệ (1–1000).",
            false
        );
        vector.ensure(generation, c.dimensions());
        tx.executeWithoutResult(s -> {
            jdbc.update("delete from knowledge_chunks where version_id=?", version);
            for (int i = 0; i < texts.size(); i++) jdbc.update(
                "insert into knowledge_chunks(version_id,ordinal,content,point_id) values(?,?,?,?)",
                version,
                i,
                texts.get(i),
                KnowledgeIds.pointId(version, i)
            );
            jdbc.update(
                "update knowledge_index_jobs set total=?,progress=0 where id=?",
                texts.size(),
                job
            );
        });
        for (int start = 0; start < texts.size(); start += 8) {
            int end = Math.min(start + 8, texts.size());
            if (
                config.view().embeddingRevision() != generation
            ) throw new AiHttpClient.RemoteFailure(
                "Cấu hình embedding đã thay đổi trong lúc lập chỉ mục.",
                false
            );
            var vectors = ai.embed(c, texts.subList(start, end));
            List<QdrantStore.Point> points = new ArrayList<>();
            for (int i = start; i < end; i++) points.add(
                new QdrantStore.Point(
                    KnowledgeIds.pointId(version, i),
                    version,
                    vectors.get(i - start)
                )
            );
            vector.upsert(generation, points);
            jdbc.update(
                "update knowledge_index_jobs set progress=?,lease_until=date_add(current_timestamp(6),interval 5 minute) where id=?",
                end,
                job
            );
        }
        tx.executeWithoutResult(s -> {
            var current = jdbc.queryForMap(
                "select embedding_revision from chatbot_config where id=1 for update"
            );
            if (
                ((Number) current.get("embedding_revision")).longValue() != generation
            ) throw new AiHttpClient.RemoteFailure("Cấu hình đã đổi; cần lập chỉ mục lại.", false);
            jdbc.update(
                "update knowledge_versions set status='READY',embedding_revision=? where id=?",
                generation,
                version
            );
            jdbc.update(
                "update knowledge_index_jobs set status='COMPLETED',lease_until=null,error=null where id=?",
                job
            );
        });
    }
}
