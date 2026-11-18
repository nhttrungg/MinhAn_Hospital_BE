package vn.antam.clinic.service.identity;

import java.util.*;

/** Business contract; implemented by the Spring-managed MfaServiceImpl. */
public interface MfaService {
    public boolean enabled(long id);

    public void password(long id, String password);

    public byte[] encrypt(String secret);

    public List<String> enable(long id, byte[] secret, String code);

    public boolean verify(long id, long version, String code);

    public void reset(long id);

    public void unlock(long id);
}
