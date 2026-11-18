package vn.antam.clinic.service.shared;

/** Business contract; implemented by the Spring-managed WorkloadGuardImpl. */
public interface WorkloadGuard {
    public void check(String field, long id);

    public record Blocker(long id, String code, String status) {}
}
