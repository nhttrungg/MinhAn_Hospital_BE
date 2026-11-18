package vn.antam.clinic.service.impl.dashboard;

import static vn.antam.clinic.service.dto.dashboard.DashboardDtos.*;
import static vn.antam.clinic.service.support.ClinicJdbc.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.security.Roles;
import vn.antam.clinic.service.dashboard.DashboardOverviewService;

@Service
@Transactional(readOnly = true)
public class DashboardOverviewServiceImpl implements DashboardOverviewService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate db;

    public DashboardOverviewServiceImpl(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public Overview overview(int days) {
        if (days != 7 && days != 30) throw bad("Chọn 7 hoặc 30 ngày.");
        require("ADMIN", "DOCTOR", "RECEPTIONIST", "CASHIER", "PHARMACIST", "LAB_TECH", "NURSE");
        String selected = List.of(
            "ADMIN",
            "DOCTOR",
            "RECEPTIONIST",
            "CASHIER",
            "PHARMACIST",
            "LAB_TECH",
            "NURSE"
        )
            .stream()
            .filter(v -> role(v))
            .findFirst()
            .orElseThrow();
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = today.minusDays(days - 1);
        Timestamp from = time(start),
            end = time(today.plusDays(1)),
            previousFrom = time(start.minusDays(days));
        List<Kpi> kpis = new ArrayList<>();
        List<Day> visits = List.of();
        List<Revenue> revenue = List.of();
        List<Slice> services = List.of(),
            doctors = List.of(),
            statuses = List.of();
        if (selected.equals("ADMIN")) {
            visits = visits(start, days, "", List.of());
            revenue = revenue(start, days, "", List.of());
            String visitSql =
                "select count(*) from appointments a join appointment_slots s on s.id=a.slot_id where a.status='COMPLETED' and s.starts_at>=? and s.starts_at<?";
            kpis.add(
                compare(
                    "completed",
                    "Lượt khám hoàn tất",
                    visitSql,
                    from,
                    end,
                    previousFrom,
                    from,
                    "lượt"
                )
            );
            kpis.add(
                compare(
                    "revenue",
                    "Thực thu",
                    netRevenueSql(""),
                    from,
                    end,
                    previousFrom,
                    from,
                    "VND"
                )
            );
            BigDecimal total = value(
                "select count(*) from appointments a join appointment_slots s on s.id=a.slot_id where s.starts_at>=? and s.starts_at<?",
                from,
                end
            );
            BigDecimal absent = value(
                "select count(*) from appointments a join appointment_slots s on s.id=a.slot_id where a.status='NO_SHOW' and s.starts_at>=? and s.starts_at<?",
                from,
                end
            );
            kpis.add(
                kpi(
                    "noShowRate",
                    "Tỷ lệ vắng hẹn",
                    total.signum() == 0
                        ? BigDecimal.ZERO
                        : absent
                              .multiply(BigDecimal.valueOf(100))
                              .divide(total, 1, RoundingMode.HALF_UP),
                    "%"
                )
            );
            kpis.add(
                kpi(
                    "pendingSchedules",
                    "Lịch chờ duyệt",
                    value(
                        "select count(*) from appointment_slots where status in ('SUBMITTED','CLOSE_REQUESTED')"
                    ),
                    "lịch"
                )
            );
            addStock(kpis, "", List.of(), today);
            kpis.add(
                kpi(
                    "rating",
                    "Đánh giá trung bình",
                    value(
                        "select coalesce(avg(rating),0) from encounter_reviews where visible=1 and created_at>=? and created_at<?",
                        from,
                        end
                    ),
                    "/5"
                )
            );
            services = slices(
                "select v.name name,count(*) value from appointments a join appointment_slots s on s.id=a.slot_id join services v on v.id=s.service_id where a.status='COMPLETED' and s.starts_at>=? and s.starts_at<? group by v.id,v.name order by value desc,v.id limit 5",
                from,
                end
            );
            doctors = slices(
                "select u.full_name name,count(*) value from encounters e join staff st on st.id=e.doctor_staff_id join users u on u.id=st.user_id where e.status='COMPLETED' and e.completed_at>=? and e.completed_at<? group by st.id,u.full_name order by value desc,st.id limit 5",
                from,
                end
            );
        } else if (selected.equals("RECEPTIONIST") || selected.equals("DOCTOR")) {
            boolean doctor = selected.equals("DOCTOR");
            String scope = doctor ? " and s.doctor_staff_id=?" : "";
            List<Object> scopeArgs = doctor ? List.of(staff(db)) : List.of();
            statuses = slices(
                "select a.status name,count(*) value from appointments a join appointment_slots s on s.id=a.slot_id where s.starts_at>=? and s.starts_at<?" +
                    scope +
                    " group by a.status",
                args(time(today), end, scopeArgs)
            );
            kpis.add(
                kpi(
                    "waiting",
                    "Đang chờ khám hôm nay",
                    value(
                        "select count(*) from appointments a join appointment_slots s on s.id=a.slot_id left join encounters e on e.appointment_id=a.id where a.status='CHECKED_IN' and a.checked_in_at>=? and a.checked_in_at<? and (e.status is null or e.status='WAITING')" +
                            scope,
                        args(time(today), end, scopeArgs)
                    ),
                    "lượt"
                )
            );
            visits = visits(start, days, scope, scopeArgs);
            if (doctor) {
                long staffId = staff(db);
                String completed =
                    "select count(*) from encounters where status='COMPLETED' and completed_at>=? and completed_at<? and doctor_staff_id=?";
                kpis.add(
                    compare(
                        "completed",
                        "Đã khám trong kỳ",
                        completed,
                        from,
                        end,
                        previousFrom,
                        from,
                        "lượt",
                        staffId
                    )
                );
                kpis.add(
                    kpi(
                        "completedWeek",
                        "Đã khám tuần này",
                        value(
                            completed,
                            time(today.with(java.time.DayOfWeek.MONDAY)),
                            end,
                            staffId
                        ),
                        "lượt"
                    )
                );
                kpis.add(
                    kpi(
                        "averageMinutes",
                        "Thời gian khám trung bình",
                        value(
                            "select coalesce(avg(timestampdiff(second,started_at,completed_at))/60,0) from encounters where doctor_staff_id=? and status='COMPLETED' and started_at is not null and completed_at>=? and completed_at<?",
                            staffId,
                            from,
                            end
                        ),
                        "phút"
                    )
                );
                kpis.add(
                    kpi(
                        "rating",
                        "Đánh giá của tôi",
                        value(
                            "select coalesce(avg(rating),0) from encounter_reviews where visible=1 and doctor_staff_id=? and created_at>=? and created_at<?",
                            staffId,
                            from,
                            end
                        ),
                        "/5"
                    )
                );
            } else {
                kpis.add(
                    kpi(
                        "confirmed",
                        "Chờ tiếp nhận hôm nay",
                        statusValue(statuses, "CONFIRMED"),
                        "lượt"
                    )
                );
                kpis.add(
                    kpi("noShow", "Vắng hẹn hôm nay", statusValue(statuses, "NO_SHOW"), "lượt")
                );
            }
        } else if (selected.equals("CASHIER")) {
            String scope =
                " and (i.counter_id is null or exists(select 1 from counter_staff cs join staff st on st.id=cs.staff_id where cs.counter_id=i.counter_id and st.user_id=? and st.active=1))";
            List<Object> scoped = List.of(user());
            // Consultation cashiers can see central invoices; pharmacy invoices require counter assignment.
            kpis.add(
                compare(
                    "revenue",
                    "Thực thu hôm nay",
                    netRevenueSql(scope),
                    time(today),
                    end,
                    time(today.minusDays(1)),
                    time(today),
                    "VND",
                    user(),
                    user()
                )
            );
            kpis.add(
                kpi(
                    "open",
                    "Hóa đơn chưa thu",
                    value("select count(*) from invoices i where i.status='OPEN'" + scope, user()),
                    "hóa đơn"
                )
            );
            kpis.add(
                kpi(
                    "partial",
                    "Hóa đơn thu một phần",
                    value(
                        "select count(*) from invoices i where i.status='PARTIAL'" + scope,
                        user()
                    ),
                    "hóa đơn"
                )
            );
            revenue = revenue(start, days, scope, scoped);
            statuses = slices(
                "select i.status name,count(*) value from invoices i where i.status<>'VOID'" +
                    scope +
                    " group by i.status",
                user()
            );
        } else if (selected.equals("PHARMACIST")) {
            String scope =
                " and exists(select 1 from counter_staff cs join staff st on st.id=cs.staff_id where cs.counter_id=b.counter_id and st.user_id=? and st.active=1)";
            addStock(kpis, scope, List.of(user()), today);
            kpis.add(
                kpi(
                    "pendingDispensing",
                    "Đơn chờ soạn",
                    value(
                        "select count(*) from pharmacy_orders o where o.status='OPEN' and exists(select 1 from counter_staff cs join staff st on st.id=cs.staff_id where cs.counter_id=o.counter_id and st.user_id=? and st.active=1) and not exists(select 1 from dispensings d where d.encounter_id=o.encounter_id and d.status in ('READY','DELIVERED'))",
                        user()
                    ),
                    "đơn"
                )
            );
            statuses = slices(
                "select d.status name,count(*) value from dispensings d where exists(select 1 from counter_staff cs join staff st on st.id=cs.staff_id where cs.counter_id=d.counter_id and st.user_id=? and st.active=1) group by d.status",
                user()
            );
        } else if (selected.equals("LAB_TECH")) {
            statuses = slices(
                "select d.status name,count(*) value from diagnostic_orders d where exists(select 1 from diagnostic_staff ds join staff st on st.id=ds.staff_id where ds.room_id=d.room_id and st.user_id=? and st.active=1) group by d.status",
                user()
            );
            kpis.add(
                kpi(
                    "pendingDiagnostics",
                    "Chỉ định đang chờ",
                    statusValue(statuses, "ISSUED").add(statusValue(statuses, "ACCEPTED")),
                    "chỉ định"
                )
            );
            kpis.add(
                kpi(
                    "inProgress",
                    "Đang thực hiện",
                    statusValue(statuses, "IN_PROGRESS"),
                    "chỉ định"
                )
            );
            kpis.add(
                kpi("completed", "Đã hoàn tất", statusValue(statuses, "COMPLETED"), "chỉ định")
            );
        } else {
            statuses = slices(
                "select e.status name,count(*) value from encounters e where e.nurse_staff_id=? and e.created_at>=? and e.created_at<? group by e.status",
                staff(db),
                time(today),
                end
            );
            kpis.add(
                kpi(
                    "assigned",
                    "Lượt được phân công hôm nay",
                    statuses.stream().map(Slice::value).reduce(BigDecimal.ZERO, BigDecimal::add),
                    "lượt"
                )
            );
        }
        return new Overview(
            selected,
            days,
            Instant.now().toString(),
            kpis,
            visits,
            revenue,
            services,
            doctors,
            statuses
        );
    }

    private void addStock(List<Kpi> kpis, String scope, List<Object> scoped, LocalDate today) {
        Map<String, Object> settings = db.queryForMap("select * from clinic_settings where id=1");
        int days = settings.get("near_expiry_days") instanceof Number n ? n.intValue() : 30;
        kpis.add(
            kpi(
                "nearExpiry",
                "Lô thuốc sắp hết hạn",
                value(
                    "select count(*) from medication_batches b where b.active=1 and b.quantity>0 and b.expiry_date>=? and b.expiry_date<=?" +
                        scope,
                    args(today, today.plusDays(days), scoped)
                ),
                "lô"
            )
        );
        kpis.add(
            kpi(
                "expired",
                "Lô thuốc hết hạn còn tồn",
                value(
                    "select count(*) from medication_batches b where b.active=1 and b.quantity>0 and b.expiry_date<?" +
                        scope,
                    prepend(List.of(today), scoped)
                ),
                "lô"
            )
        );
        kpis.add(
            kpi(
                "lowStock",
                "Thuốc tồn thấp (dưới 10)",
                value(
                    "select count(*) from (select b.counter_id,b.medication_id from medication_batches b where b.active=1 and b.expiry_date>=?" +
                        scope +
                        " group by b.counter_id,b.medication_id having sum(b.quantity-b.reserved)<10) low_stock",
                    prepend(List.of(today), scoped)
                ),
                "thuốc/quầy"
            )
        );
    }

    private List<Day> visits(LocalDate start, int days, String scope, List<Object> scoped) {
        List<Map<String, Object>> rows = db.queryForList(
            "select date(convert_tz(s.starts_at,'+00:00','+07:00')) day,sum(a.status='COMPLETED') completed,sum(a.status='CANCELLED') cancelled,sum(a.status='NO_SHOW') no_show from appointments a join appointment_slots s on s.id=a.slot_id where s.starts_at>=? and s.starts_at<?" +
                scope +
                " group by day",
            args(time(start), time(start.plusDays(days)), scoped)
        );
        Map<String, Map<String, Object>> dates = new HashMap<>();
        rows.forEach(r -> dates.put(String.valueOf(r.get("day")), r));
        List<Day> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            String day = start.plusDays(i).toString();
            var r = dates.get(day);
            result.add(
                new Day(day, count(r, "completed"), count(r, "cancelled"), count(r, "no_show"))
            );
        }
        return result;
    }

    private List<Revenue> revenue(LocalDate start, int days, String scope, List<Object> scoped) {
        String sql =
            "select day,sum(amount) amount from (select date(convert_tz(r.recorded_at,'+00:00','+07:00')) day,r.amount from receipts r join invoices i on i.id=r.invoice_id where r.recorded_at>=? and r.recorded_at<?" +
            scope +
            " union all select date(convert_tz(r.recorded_at,'+00:00','+07:00')) day,-r.amount from refunds r join invoices i on i.id=r.invoice_id where r.recorded_at>=? and r.recorded_at<?" +
            scope +
            ") movements group by day";
        List<Object> params = new ArrayList<>(
            List.of(args(time(start), time(start.plusDays(days)), scoped))
        );
        params.addAll(List.of(args(time(start), time(start.plusDays(days)), scoped)));
        Map<String, BigDecimal> dates = new HashMap<>();
        db.queryForList(sql, params.toArray()).forEach(r ->
            dates.put(String.valueOf(r.get("day")), new BigDecimal(r.get("amount").toString()))
        );
        List<Revenue> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            String day = start.plusDays(i).toString();
            result.add(new Revenue(day, dates.getOrDefault(day, BigDecimal.ZERO)));
        }
        return result;
    }

    private String netRevenueSql(String scope) {
        return (
            "select coalesce(sum(amount),0) from (select r.amount from receipts r join invoices i on i.id=r.invoice_id where r.recorded_at>=? and r.recorded_at<?" +
            scope +
            " union all select -r.amount from refunds r join invoices i on i.id=r.invoice_id where r.recorded_at>=? and r.recorded_at<?" +
            scope +
            ") net"
        );
    }

    private Kpi compare(
        String key,
        String label,
        String sql,
        Timestamp from,
        Timestamp end,
        Timestamp previousFrom,
        Timestamp previousEnd,
        String unit,
        Object... scope
    ) {
        boolean net = key.equals("revenue");
        Object[] current = net ? netArgs(from, end, scope) : args(from, end, List.of(scope));
        Object[] previous = net
            ? netArgs(previousFrom, previousEnd, scope)
            : args(previousFrom, previousEnd, List.of(scope));
        BigDecimal value = value(sql, current),
            old = value(sql, previous);
        return new Kpi(
            key,
            label,
            value,
            old,
            unit,
            old.signum() == 0
                ? null
                : value
                      .subtract(old)
                      .multiply(BigDecimal.valueOf(100))
                      .divide(old.abs(), 1, RoundingMode.HALF_UP)
        );
    }

    private Object[] netArgs(Object from, Object end, Object[] scope) {
        int half = scope.length / 2;
        List<Object> result = new ArrayList<>(List.of(from, end));
        result.addAll(Arrays.asList(scope).subList(0, half));
        result.add(from);
        result.add(end);
        result.addAll(Arrays.asList(scope).subList(half, scope.length));
        return result.toArray();
    }

    private Kpi kpi(String key, String label, BigDecimal value, String unit) {
        return new Kpi(key, label, value.setScale(1, RoundingMode.HALF_UP), null, unit, null);
    }

    private BigDecimal value(String sql, Object... params) {
        BigDecimal result = db.queryForObject(sql, BigDecimal.class, params);
        return result == null ? BigDecimal.ZERO : result;
    }

    private List<Slice> slices(String sql, Object... args) {
        return db.query(
            sql,
            (rs, n) -> new Slice(rs.getString("name"), rs.getBigDecimal("value")),
            args
        );
    }

    private static Timestamp time(LocalDate date) {
        return Timestamp.from(date.atStartOfDay(ZONE).toInstant());
    }

    private static Object[] args(Object from, Object end, List<Object> scoped) {
        return prepend(List.of(from, end), scoped);
    }

    private static Object[] prepend(List<?> first, List<?> last) {
        List<Object> out = new ArrayList<>(first);
        out.addAll(last);
        return out.toArray();
    }

    private static long count(Map<String, Object> row, String key) {
        return row == null ? 0 : ((Number) row.get(key)).longValue();
    }

    private static BigDecimal statusValue(List<Slice> rows, String status) {
        return rows
            .stream()
            .filter(s -> s.name().equals(status))
            .map(Slice::value)
            .findFirst()
            .orElse(BigDecimal.ZERO);
    }
}
