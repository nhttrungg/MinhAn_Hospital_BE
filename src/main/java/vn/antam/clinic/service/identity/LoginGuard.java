package vn.antam.clinic.service.identity;

/** Business contract; implemented by the Spring-managed LoginGuardImpl. */
public interface LoginGuard {
    public void check(String username);

    public boolean failure(String username);

    public void success(long userId);
}
