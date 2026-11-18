package vn.antam.clinic.service.impl.clinical;

import java.text.Normalizer;
import java.util.*;

/** Demo matching only: no synonym, dose, class allergy or contraindication inference. */
public final class ClinicalText {

    private ClinicalText() {}

    public static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replace('đ', 'd')
            .replaceAll("\\s+", " ")
            .trim();
    }

    public static Set<String> ingredients(String text) {
        var result = new LinkedHashSet<String>();
        for (String part : normalize(text).split("[+;,/|]")) {
            if (!part.isBlank()) result.add(part.trim());
        }
        return result;
    }

    public static boolean containsIngredient(String note, String ingredient) {
        String normalized = normalize(note);
        if (ingredient.isBlank()) return false;
        return java.util.regex.Pattern.compile(
            "(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(ingredient) + "(?![\\p{L}\\p{N}])"
        )
            .matcher(normalized)
            .find();
    }
}
