package org.icarus.tingere.gui;

import org.icarus.tingere.recipe.Ingredient;

/** 材料匹配模式，对应 {@code Ingredient} 的 match-mode 字段。 */
public enum MatchMode {

    // 具体取值由 Ingredient 定义，这里引用过去，免得两边各写一份字符串跑偏

    /** 缺省档：材质对上、且物品带着配方里写的组件（允许多余的），不需要写 match-mode。 */
    CONTAIN("包含", null),
    EXACT("精确", "exact"),
    MATERIAL("仅材质", Ingredient.MODE_MATERIAL);

    private final String display;
    private final String yamlValue;

    MatchMode(String display, String yamlValue) {
        this.display = display;
        this.yamlValue = yamlValue;
    }

    public String display() {
        return display;
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

