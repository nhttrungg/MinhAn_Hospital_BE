package vn.antam.clinic.service.impl.chatbot;

import static vn.antam.clinic.service.dto.chatbot.ChatTypes.*;
import static vn.antam.clinic.service.support.chatbot.ChatText.*;

import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.antam.clinic.integration.ai.AiHttpClient;
import vn.antam.clinic.service.chatbot.ChatSessionStore;
import vn.antam.clinic.service.support.ClinicJdbc;
import vn.antam.clinic.web.rest.errors.ApiException;

@Service
public class ChatSessionStoreImpl implements ChatSessionStore {

    private final JdbcTemplate jdbc;
    private final AiHttpClient json;
    private final Clock clock;
    private final org.springframework.integration.redis.util.RedisLockRegistry locks;
    private final vn.antam.clinic.security.RedisRateLimiter limiter;

    public ChatSessionStoreImpl(
        JdbcTemplate jdbc,
        AiHttpClient json,
        Clock clock,
        org.springframework.integration.redis.util.RedisLockRegistry locks,
        vn.antam.clinic.security.RedisRateLimiter limiter
    ) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.locks = locks;
        this.limiter = limiter;
    }

    @Override
    public LockHandle lock(HttpSession session) {
        var lock = locks.obtain(session.getId());
        try {
            if (!lock.tryLock(2, java.util.concurrent.TimeUnit.SECONDS)) throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Hệ thống đang bận",
                "Vui lòng thử lại sau vài giây."
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Hệ thống đang bận",
                "Vui lòng thử lại sau vài giây."
            );
        }
        return lock::unlock;
    }

    @Override
    public String ensure(HttpSession session) {
        String id = (String) session.getAttribute(ATTRIBUTE);
        if (
            id != null &&
            jdbc.queryForObject(
                "select count(*) from chat_sessions where id=? and expires_at>?",
                Integer.class,
                id,
                java.sql.Timestamp.from(clock.instant())
            ) == 1
        ) return id;
        if (id != null) jdbc.update("delete from chat_sessions where id=?", id);
        id = UUID.randomUUID().toString();
        jdbc.update(
            "insert into chat_sessions(id,expires_at) values(?,?)",
            id,
            java.sql.Timestamp.from(clock.instant().plus(Duration.ofHours(24)))
        );
        session.setAttribute(ATTRIBUTE, id);
        return id;
    }

    @Override
    public Optional<Reply> duplicate(String id, MessageRequest request) {
        var rows = jdbc.queryForList(
            "select request_hash,response_json from chat_messages where session_id=? and client_message_id=?",
            id,
            request.clientMessageId()
        );
        if (rows.isEmpty()) return Optional.empty();
        if (
            !AiHttpClient.hash(request.message().strip()).equals(
                rows.getFirst().get("request_hash")
            )
        ) throw ClinicJdbc.conflict("Mã gửi lặp đã dùng cho câu hỏi khác.");
        try {
            return Optional.of(
                json.json.readValue((String) rows.getFirst().get("response_json"), Reply.class)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được phản hồi đã lưu.");
        }
    }

    @Override
    public void quota(String id, String ip) {
        long minute = clock.instant().getEpochSecond() / 60;
        try {
            if (!limiter.allow("chat-ip", ip, 60)) throw limited();
        } catch (org.springframework.dao.DataAccessException unavailable) {
            throw new vn.antam.clinic.web.rest.errors.ApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Hệ thống đang bận",
                "Vui lòng thử lại sau vài giây."
            );
        }
        int changed = jdbc.update(
            "update chat_sessions set rate_count=case when rate_window=? then rate_count+1 else 1 end,rate_window=? where id=? and (rate_window<>? or rate_count<10)",
            minute,
            minute,
            id,
            minute
        );
        if (changed == 0) throw limited();
    }

    private static ApiException limited() {
        return new ApiException(
            HttpStatus.TOO_MANY_REQUESTS,
            "Bạn đã hỏi nhiều lần",
            "Vui lòng chờ khoảng một phút rồi thử lại."
        );
    }

    @Override
    public List<Map<String, Object>> history(String id) {
        var turns = jdbc.query(
            "select question,response_json from chat_messages where session_id=? order by id desc limit 12",
            (r, n) -> {
                try {
                    return Map.of(
                        "question",
                        r.getString(1),
                        "answer",
                        json.json.readValue(r.getString(2), Reply.class).answer()
                    );
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    throw new IllegalStateException("Không đọc được lịch sử trò chuyện.", e);
                }
            },
            id
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = turns.size() - 1; i >= 0; i--) {
            var turn = turns.get(i);
            result.add(Map.of("role", "user", "content", bounded(turn.get("question"), 2000)));
            result.add(
                Map.of("role", "assistant", "content", bounded(redact(turn.get("answer")), 1500))
            );
        }
        return result;
    }

    private static String bounded(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    @Override
    public Map<String, String> lookupContext(String id) {
        String stored = jdbc.queryForObject(
            "select lookup_context_json from chat_sessions where id=?",
            String.class,
            id
        );
        if (stored == null) return Map.of();
        try {
            var node = json.json.readTree(stored);
            Map<String, String> result = new LinkedHashMap<>();
            for (String key : List.of("kind", "keyword", "gender", "date"))
                if (node.hasNonNull(key)) result.put(key, node.path(key).asText());
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Không đọc được bộ lọc trò chuyện.", e);
        }
    }

    @Override
    public com.fasterxml.jackson.databind.node.ObjectNode mergeLookupContext(
        String id,
        com.fasterxml.jackson.databind.node.ObjectNode current
    ) {
        var result = current.deepCopy();
        var previous = lookupContext(id);
        Set<String> cleared = new HashSet<>();
        if (current.path("clearFilters").isArray()) for (var key : current.path("clearFilters"))
            cleared.add(key.asText());
        if (!current.path("resetFilters").asBoolean(false)) for (String key : List.of(
            "keyword",
            "gender",
            "date"
        ))
            if (
                !current.has(key) && !cleared.contains(key) && previous.containsKey(key)
            ) result.put(key, previous.get(key));
        for (String key : cleared)
            if (Set.of("keyword", "gender", "date").contains(key)) result.remove(key);
        return result;
    }

    @Override
    public void rememberLookupContext(
        String id,
        com.fasterxml.jackson.databind.node.ObjectNode filters
    ) {
        var state = json.json.createObjectNode();
        for (String key : List.of("kind", "keyword", "gender", "date")) {
            String value = filters.path(key).asText("").strip();
            if (!value.isBlank()) state.put(key, bounded(redact(value), 200));
        }
        jdbc.update(
            "update chat_sessions set lookup_context_json=? where id=?",
            state.toString(),
            id
        );
    }

    @Override
    public void save(String id, MessageRequest request, String sanitized, Reply reply) {
        try {
            jdbc.update(
                "insert into chat_messages(session_id,client_message_id,request_hash,question,response_json) values(?,?,?,?,?)",
                id,
                request.clientMessageId(),
                AiHttpClient.hash(request.message().strip()),
                sanitized,
                json.json.writeValueAsString(reply)
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void reset(HttpSession session) {
        if (session == null) return;
        try (var held = lock(session)) {
            Object id = session.getAttribute(ATTRIBUTE);
            session.removeAttribute(ATTRIBUTE);
            if (id instanceof String s) jdbc.update("delete from chat_sessions where id=?", s);
        }
    }

    @Override
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(
        name = "chat-cleanup",
        lockAtMostFor = "30m",
        lockAtLeastFor = "2s"
    )
    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    public void clean() {
        jdbc.update(
            "delete from chat_sessions where expires_at<?",
            java.sql.Timestamp.from(clock.instant())
        );
    }
}
