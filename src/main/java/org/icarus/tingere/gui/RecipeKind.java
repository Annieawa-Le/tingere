package org.icarus.tingere.gui;

/** 第一版开放编辑的配方类型。 */
public enum RecipeKind {

    SHAPED("shaped", "有序"),
    SHAPELESS("shapeless", "无序");

    private final String id;
    private final String display;

    RecipeKind(String id, String display) {
        this.id = id;
        this.display = display;
    }

    /** yaml 里 type 字段的值。 */
    public String id() {
        return id;
    }

    public String display() {
        return display;
    }
}
