package vn.antam.clinic.service.demo;

/** Business contract; implemented by the Spring-managed UpgradeFeaturesSeederImpl. */
public interface UpgradeFeaturesSeeder {
    public static final String DATASET = "upgrade-features-v1";

    public boolean initialize();
}
