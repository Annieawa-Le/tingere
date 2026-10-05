package org.icarus.tingere.gui;

import org.icarus.tingere.recipe.Ingredient;

/** 材料匹配模式，对应 {@code Ingredient} 的 match-mode 字段。 */
public enum MatchMode {

    // 具体取值由 Ingredient 定义，这里引用过去，免得两边各写一份字符串跑偏

    CONTAIN("包含", Ingredient.MODE_CONTAIN),
    EXACT("精确", Ingredient.MODE_EXACT),
    MATERIAL("仅材质", Ingredient.MODE_MATERIAL);

    private final String yamlValue;

    MatchMode(String display, String yamlValue) {
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

