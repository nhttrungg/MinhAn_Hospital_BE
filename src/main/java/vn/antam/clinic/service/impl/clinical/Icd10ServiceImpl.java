package vn.antam.clinic.service.impl.clinical;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.antam.clinic.service.clinical.Icd10Service;
import vn.antam.clinic.service.dto.clinical.EncounterDtos.*;
import vn.antam.clinic.service.support.ClinicJdbc;

@Service
public class Icd10ServiceImpl implements Icd10Service {

    private final JdbcTemplate jdbc;

    public Icd10ServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    @org.springframework.cache.annotation.Cacheable(cacheNames = "icd10.search", sync = true)
    public List<Code> search(String query) {
        ClinicJdbc.require("DOCTOR", "ADMIN");
        String q = ClinicalText.normalize(query);
        if (q.length() > 100) throw ClinicJdbc.bad("Từ khóa quá dài.");
        // Small, bounded demo catalog; Java normalization supports accent-free Vietnamese search.
        return jdbc
            .query(
                "select code,name_vi,name_en,source_system,is_demo from icd10_codes where active=1 order by code",
                (r, n) ->
                    new Code(
                        r.getString(1),
                        r.getString(2),
                        r.getString(3),
                        r.getString(4),
                        r.getBoolean(5)
                    )
            )
            .stream()
            .filter(c ->
                ClinicalText.normalize(c.code() + " " + c.nameVi() + " " + c.nameEn()).contains(q)
            )
            .limit(50)
            .toList();
    }

    @Override
    public List<DiagnosisView> diagnoses(long encounter) {
        return jdbc.query(
            "select d.icd_code,c.name_vi,c.name_en,d.is_primary,c.source_system,c.is_demo from encounter_diagnoses d join icd10_codes c on c.code=d.icd_code where d.encounter_id=? order by d.is_primary desc,d.icd_code",
            (r, n) ->
                new DiagnosisView(
                    r.getString(1),
                    r.getString(2),
                    r.getString(3),
                    r.getBoolean(4),
                    r.getString(5),
                    r.getBoolean(6)
                ),
            encounter
        );
    }

    @Override
    public void save(long encounter, List<DiagnosisInput> diagnoses) {
        // Null preserves diagnoses for older API clients; [] explicitly clears them.
        if (diagnoses == null) return;
        if (diagnoses.size() > 20) throw ClinicJdbc.bad("Tối đa 20 chẩn đoán.");
        var seen = new HashSet<String>();
        int primary = 0;
        for (var d : diagnoses) {
            if (d == null || d.code() == null) throw ClinicJdbc.bad("Mã chẩn đoán không hợp lệ.");
            String code = d.code().trim().toUpperCase(Locale.ROOT);
            if (!seen.add(code)) throw ClinicJdbc.bad("Mã chẩn đoán bị trùng.");
            if (d.primary()) primary++;
            if (
                jdbc.queryForObject(
                    "select count(*) from icd10_codes where code=? and active=1",
                    Integer.class,
                    code
                ) == 0
            ) throw ClinicJdbc.bad("Mã chẩn đoán không tồn tại hoặc đã ngưng: " + code);
        }
        if (!diagnoses.isEmpty() && primary != 1) throw ClinicJdbc.bad(
            "Chọn đúng một chẩn đoán chính."
        );
        jdbc.update("delete from encounter_diagnoses where encounter_id=?", encounter);
        for (var d : diagnoses)
            jdbc.update(
                "insert into encounter_diagnoses(encounter_id,icd_code,is_primary) values(?,?,?)",
                encounter,
                d.code().trim().toUpperCase(Locale.ROOT),
                d.primary()
            );
    }
}
