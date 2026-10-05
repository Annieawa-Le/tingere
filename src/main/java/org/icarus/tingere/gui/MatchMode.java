package org.icarus.tingere.gui;

import org.icarus.tingere.recipe.Ingredient;

public enum MatchMode {

    CONTAIN(Ingredient.MODE_CONTAIN),
    EXACT(Ingredient.MODE_EXACT),
    MATERIAL(Ingredient.MODE_MATERIAL);

    private final String yamlValue;

    MatchMode(String yamlValue) {
        this.yamlValue = yamlValue;
    }

    public String yamlValue() {
        return yamlValue;
    }

    public static MatchMode fromYaml(String raw) {
        if (raw != null && !raw.isBlank()) {
            String value = raw.trim();
            for (MatchMode mode : values()) {
                if (value.equalsIgnoreCase(mode.yamlValue)) {
                    return mode;
                }
            }
        }
        return CONTAIN;
    }
}

