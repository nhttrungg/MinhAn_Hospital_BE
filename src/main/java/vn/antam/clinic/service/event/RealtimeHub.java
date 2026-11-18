package vn.antam.clinic.service.event;

import static vn.antam.clinic.service.support.ClinicJdbc.*;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.antam.clinic.domain.event.ClinicEvent;
import vn.antam.clinic.security.*;
import vn.antam.clinic.service.realtime.QueueDisplayService;

@Component
public class RealtimeHub {

    static final long TIMEOUT_MS = 35 * 60 * 1000L;
    private static final Set<String> TYPES = Set.of(
        "queue.updated",
        "encounter.updated",
        "billing.updated",
        "payment.updated",
        "notification.updated",
        "pharmacy.updated",
        "diagnostic.updated"
    );
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private final Map<Long, Set<Connection>> users = new ConcurrentHashMap<>(),
        doctors = new ConcurrentHashMap<>(),
        rooms = new ConcurrentHashMap<>();
    private final Set<Connection> reception = ConcurrentHashMap.newKeySet(),
        publicAll = ConcurrentHashMap.newKeySet(),
        publicConnections = ConcurrentHashMap.newKeySet();
    private final ReentrantLock registration = new ReentrantLock();
    private final JdbcTemplate db;
    private final QueueDisplayService display;
    private final SessionStateCache states;
    private final ThreadPoolExecutor outbound;
    private final int maxConnections, maxPerUser, maxPublic;
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean stopping;

    record Scope(
        boolean publicDisplay,
        Set<Long> rooms,
        boolean reception,
        Long doctor,
        Long user,
        long sessionVersion
    ) {
        Scope {
            rooms = Set.copyOf(rooms);
        }

        boolean accepts(ClinicEvent e) {
            if (publicDisplay) return (
                "queue.updated".equals(e.type()) &&
                e.roomId() != null &&
                (rooms.isEmpty() || rooms.contains(e.roomId()))
            );
            return (
                (user != null && user.equals(e.userId())) ||
                (doctor != null &&
                    doctor.equals(e.doctorStaffId()) &&
                    Set.of("queue.updated", "encounter.updated").contains(e.type())) ||
                (reception && Set.of("queue.updated", "encounter.updated").contains(e.type()))
            );
        }
    }

    private static final class Connection {

        final Scope scope;
        final SseEmitter emitter;
        final AtomicBoolean queued = new AtomicBoolean(),
            closed = new AtomicBoolean();
        final Set<String> pending = ConcurrentHashMap.newKeySet();
        final ReentrantLock sending = new ReentrantLock();

        Connection(Scope scope, SseEmitter emitter) {
            this.scope = scope;
            this.emitter = emitter;
        }
    }

    @Autowired
    public RealtimeHub(
        JdbcTemplate db,
        QueueDisplayService display,
        SessionStateCache states,
        @Value("${app.realtime.max-connections:3000}") int maxConnections,
        @Value("${app.realtime.max-per-user:5}") int maxPerUser,
        @Value("${app.realtime.max-public-connections:1500}") int maxPublic
    ) {
        this(
            db,
            display,
            states,
            maxConnections,
            maxPerUser,
            maxPublic,
            new ThreadPoolExecutor(
                8,
                32,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(5000),
                r -> {
                    Thread t = new Thread(r, "clinic-sse");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
            )
        );
    }

    public RealtimeHub(JdbcTemplate db, QueueDisplayService display) {
        this(db, display, new SessionStateCache(db, Duration.ofSeconds(5)), 3000, 5, 1500);
    }

    RealtimeHub(
        JdbcTemplate db,
        QueueDisplayService display,
        SessionStateCache states,
        int maxConnections,
        int maxPerUser,
        int maxPublic,
        ThreadPoolExecutor outbound
    ) {
        this.db = db;
        this.display = display;
        this.states = states;
        this.maxConnections = maxConnections;
        this.maxPerUser = maxPerUser;
        this.maxPublic = maxPublic;
        this.outbound = outbound;
    }

    public SseEmitter publicStream(Set<Long> rooms) {
        display.requireEnabled();
        return connect(new Scope(true, rooms, false, null, null, 0));
    }

    public SseEmitter staffStream() {
        ClinicUserPrincipal p = SecurityUtils.currentPrincipal();
        return connect(
            new Scope(
                false,
                Set.of(),
                role("ADMIN") || role("RECEPTIONIST"),
                role("DOCTOR") ? staff(db) : null,
                p.getId(),
                p.getSessionVersion()
            )
        );
    }

    private SseEmitter connect(Scope scope) {
        Connection c = new Connection(scope, newEmitter());
        registration.lock();
        try {
            int same = scope.publicDisplay
                ? publicConnections.size()
                : users.getOrDefault(scope.user, Set.of()).size();
            if (
                stopping ||
                connections.size() >= maxConnections ||
                same >= (scope.publicDisplay ? maxPublic : maxPerUser)
            ) throw new vn.antam.clinic.web.rest.errors.ApiException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Quá nhiều kết nối",
                "Vui lòng đóng bớt màn hình và thử lại."
            );
            c.emitter.onCompletion(() -> remove(c));
            c.emitter.onTimeout(() -> close(c));
            c.emitter.onError(e -> close(c));
            connections.add(c);
            if (scope.publicDisplay) {
                publicConnections.add(c);
                if (scope.rooms.isEmpty()) publicAll.add(c);
                else scope.rooms.forEach(id -> add(rooms, id, c));
            } else {
                add(users, scope.user, c);
                if (scope.doctor != null) add(doctors, scope.doctor, c);
                if (scope.reception) reception.add(c);
            }
        } finally {
            registration.unlock();
        }
        send(c, "ready");
        return c.emitter;
    }

    private static void add(Map<Long, Set<Connection>> index, long id, Connection c) {
        index.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(c);
    }

    private static void erase(Map<Long, Set<Connection>> index, Long id, Connection c) {
        if (id == null) return;
        Set<Connection> set = index.get(id);
        if (set != null) {
            set.remove(c);
            if (set.isEmpty()) index.remove(id, set);
        }
    }

    public void publish(ClinicEvent e) {
        if (!TYPES.contains(e.type())) return;
        Set<Connection> targets = new HashSet<>();
        if (e.userId() != null) targets.addAll(users.getOrDefault(e.userId(), Set.of()));
        if (Set.of("queue.updated", "encounter.updated").contains(e.type())) {
            targets.addAll(reception);
            if (e.doctorStaffId() != null) targets.addAll(
                doctors.getOrDefault(e.doctorStaffId(), Set.of())
            );
        }
        if ("queue.updated".equals(e.type()) && e.roomId() != null) {
            targets.addAll(publicAll);
            targets.addAll(rooms.getOrDefault(e.roomId(), Set.of()));
        }
        for (Connection c : targets) if (c.scope.accepts(e)) send(c, e.type());
    }

    private void send(Connection c, String type) {
        if (c.closed.get()) return;
        c.pending.add(type == null ? "ping" : type);
        schedule(c);
    }

    private void schedule(Connection c) {
        if (c.closed.get() || !c.queued.compareAndSet(false, true)) return;
        try {
            outbound.execute(() -> drain(c));
        } catch (RejectedExecutionException busy) {
            c.queued.set(false);
            dropped.incrementAndGet(); /* retain types; next heartbeat retries */
        }
    }

    private void drain(Connection c) {
        c.sending.lock();
        try {
            if (c.closed.get()) return;
            for (String type : new HashSet<>(c.pending)) {
                if (!c.pending.remove(type)) continue;
                if ("ping".equals(type)) c.emitter.send(
                    SseEmitter.event().comment("ping").name("ping").data("{}")
                );
                else c.emitter.send(SseEmitter.event().name(type).reconnectTime(2000).data("{}"));
            }
        } catch (IOException | RuntimeException disconnected) {
            close(c);
        } finally {
            c.sending.unlock();
            c.queued.set(false);
            if (!c.pending.isEmpty() && !c.closed.get()) schedule(c);
        }
    }

    @Scheduled(fixedRate = 20000)
    public void heartbeat() {
        Map<Long, SessionStateCache.State> active;
        try {
            active = states.refresh(Set.copyOf(users.keySet()));
        } catch (RuntimeException unavailable) {
            active = Map.of();
        }
        boolean enabled = publicConnections.isEmpty() || display.enabled();
        for (Connection c : connections) {
            var state = c.scope.user == null ? null : active.get(c.scope.user);
            if (
                c.scope.publicDisplay
                    ? !enabled
                    : state == null || !state.active() || state.version() != c.scope.sessionVersion
            ) close(c);
            else send(c, null);
        }
    }

    private boolean remove(Connection c) {
        if (!c.closed.compareAndSet(false, true)) return false;
        registration.lock();
        try {
            connections.remove(c);
            publicConnections.remove(c);
            publicAll.remove(c);
            reception.remove(c);
            erase(users, c.scope.user, c);
            erase(doctors, c.scope.doctor, c);
            c.scope.rooms.forEach(id -> erase(rooms, id, c));
            c.pending.clear();
        } finally {
            registration.unlock();
        }
        return true;
    }

    private void close(Connection c) {
        if (remove(c)) try {
            c.emitter.complete();
        } catch (IllegalStateException alreadyCompleted) {
            /* Container already completed this async request. */
        }
    }

    @PreDestroy
    public void shutdown() {
        stopping = true;
        connections.forEach(this::close);
        outbound.shutdownNow();
    }

    public int connectionCount() {
        return connections.size();
    }

    public int capacity() {
        return maxConnections;
    }

    public int pendingSendCount() {
        return outbound.getQueue().size();
    }

    public long droppedSendCount() {
        return dropped.get();
    }

    SseEmitter newEmitter() {
        return new SseEmitter(TIMEOUT_MS);
    }
}
