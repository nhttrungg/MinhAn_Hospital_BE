package vn.antam.clinic.config.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.antam.clinic.service.demo.ClinicalDemoCatalogSeeder;

@Component
@ConditionalOnProperty(name = "app.mock-data.enabled", havingValue = "true")
public class ClinicalDemoCatalogInitializer implements ApplicationRunner {

    private final ClinicalDemoCatalogSeeder seeder;

    public ClinicalDemoCatalogInitializer(ClinicalDemoCatalogSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seeder.initialize();
    }
}
