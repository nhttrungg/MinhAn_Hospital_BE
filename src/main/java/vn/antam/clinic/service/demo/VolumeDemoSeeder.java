package vn.antam.clinic.service.demo;

import java.time.*;
import java.util.*;

/** Business contract; implemented by the Spring-managed VolumeDemoSeederImpl. */
public interface VolumeDemoSeeder {
    public static final String DATASET = "outpatient-volume-v1";

    public boolean initialize();
}
