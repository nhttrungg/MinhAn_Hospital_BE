package vn.antam.clinic.service.impl.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.demo.ClinicalDemoCatalogSeeder;

/** Opt-in demonstration subset. Not a verified national ICD catalog or prescribing engine. */
@Service
public class ClinicalDemoCatalogSeederImpl implements ClinicalDemoCatalogSeeder {

    private final JdbcTemplate jdbc;

    public ClinicalDemoCatalogSeederImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void initialize() {
        try (var stream = new ClassPathResource("demo/clinical-catalog.json").getInputStream()) {
            var data = new ObjectMapper().readTree(stream);
            for (var c : data.path("codes"))
                jdbc.update(
                    "insert into icd10_codes(code,name_vi,name_en,chapter,source_system,source_url,is_demo) values(?,?,?,?,?,?,1) on duplicate key update code=code",
                    c.path("code").asText(),
                    c.path("nameVi").asText(),
                    c.path("nameEn").asText(),
                    c.path("chapter").asText(),
                    c.path("sourceSystem").asText(),
                    c.path("sourceUrl").asText()
                );
            for (var p : data.path("interactions"))
                jdbc.update(
                    "insert into drug_interactions(ingredient_a,ingredient_b,severity,note,source_url,is_demo) values(?,?,?,?,?,1) on duplicate key update ingredient_a=ingredient_a",
                    p.path("ingredientA").asText(),
                    p.path("ingredientB").asText(),
                    p.path("severity").asText(),
                    p.path("note").asText(),
                    p.path("sourceUrl").asText()
                );
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read demo clinical catalog", e);
        }
    }
}
