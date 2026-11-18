package vn.antam.clinic.service.impl.clinical;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.service.clinical.PrescriptionSafetyService;
import vn.antam.clinic.service.support.ClinicJdbc;

@Service
public class PrescriptionSafetyServiceImpl implements PrescriptionSafetyService {

    private final JdbcTemplate jdbc;

    public PrescriptionSafetyServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Warning> check(long patientId, List<Long> medicationIds) {
        var meds = new ArrayList<Medication>();
        // Preserve repeated selections: the same medication twice is also a duplicate ingredient.
        for (Long id : medicationIds) {
            if (id == null || id <= 0) throw ClinicJdbc.bad("Thuốc không hợp lệ.");
            var found = jdbc.query(
                "select id,name,ingredient from medications where id=? and active=1",
                (r, n) ->
                    new Medication(
                        r.getLong(1),
                        r.getString(2),
                        ClinicalText.ingredients(r.getString(3))
                    ),
                id
            );
            if (found.isEmpty()) throw ClinicJdbc.bad("Thuốc không còn hoạt động: #" + id);
            meds.add(found.getFirst());
        }
        var allergies = jdbc.query(
            "select ingredient,reaction,severity from patient_allergies where patient_id=? order by id",
            (r, n) ->
                new Allergy(
                    ClinicalText.ingredients(r.getString(1)),
                    r.getString(2),
                    r.getString(3)
                ),
            patientId
        );
        var legacy = jdbc.queryForObject(
            "select allergies from patient_profiles where id=?",
            String.class,
            patientId
        );
        var interactions = jdbc.query(
            "select ingredient_a,ingredient_b,severity,note from drug_interactions order by id",
            (r, n) ->
                new Interaction(
                    ClinicalText.normalize(r.getString(1)),
                    ClinicalText.normalize(r.getString(2)),
                    r.getString(3),
                    r.getString(4)
                )
        );
        return evaluate(meds, allergies, legacy, interactions);
    }

    record Medication(long id, String name, Set<String> ingredients) {}

    record Allergy(Set<String> ingredients, String reaction, String severity) {}

    record Interaction(String a, String b, String severity, String note) {}

    static List<Warning> evaluate(
        List<Medication> meds,
        List<Allergy> allergies,
        String legacy,
        List<Interaction> pairs
    ) {
        var warnings = new ArrayList<Warning>();
        for (var m : meds) {
            for (String ingredient : m.ingredients()) {
                boolean structured = allergies
                    .stream()
                    .anyMatch(a -> a.ingredients().contains(ingredient));
                if (structured || ClinicalText.containsIngredient(legacy, ingredient)) warnings.add(
                    new Warning(
                        "ALLERGY",
                        "MAJOR",
                        "Dị ứng đã ghi nhận: " + ingredient + " · " + m.name(),
                        List.of(m.id())
                    )
                );
            }
        }
        for (int i = 0; i < meds.size(); i++) for (int j = i + 1; j < meds.size(); j++) {
            var a = meds.get(i);
            var b = meds.get(j);
            var shared = new LinkedHashSet<>(a.ingredients());
            shared.retainAll(b.ingredients());
            var ids = List.of(a.id(), b.id());
            if (!shared.isEmpty()) warnings.add(
                new Warning(
                    "DUPLICATE_INGREDIENT",
                    "MODERATE",
                    "Trùng hoạt chất: " + String.join(", ", shared),
                    ids
                )
            );
            for (var pair : pairs)
                if (
                    (a.ingredients().contains(pair.a()) && b.ingredients().contains(pair.b())) ||
                    (a.ingredients().contains(pair.b()) && b.ingredients().contains(pair.a()))
                ) warnings.add(new Warning("INTERACTION", pair.severity(), pair.note(), ids));
        }
        return List.copyOf(warnings);
    }

    public static boolean requiresOverride(List<Warning> warnings) {
        return warnings
            .stream()
            .anyMatch(w -> w.type().equals("ALLERGY") || w.severity().equals("MAJOR"));
    }
}
