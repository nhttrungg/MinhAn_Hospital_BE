package vn.antam.clinic.service.identity;

import java.security.*;
import java.util.*;

/** Business contract; implemented by the Spring-managed TotpServiceImpl. */
public interface TotpService {
    public String secret();

    public String code(String secret, long epochSeconds, int digits);

    public long matchingStep(String secret, String code, long epochSeconds);

    public List<String> recoveryCodes();
}
