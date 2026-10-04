package org.icarus.tingere.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.component.ComponentApplyer;
import org.icarus.tingere.nms.ComponentSnbt;

import java.util.Iterator;
import java.util.Locale;

public record Ingredient(@JsonProperty(required = true) Material material,
                         Integer amount,
                         String matchMode,
                         JsonNode components) {

    public static final int DEFAULT_AMOUNT = 1;

    /** 只认材质，yaml 里写 {@code match-mode: material}。 */
    public static final String MODE_MATERIAL = "material";
    /** 材质对上、且物品带着这里写的组件（允许多余的），yaml 里写 {@code match-mode: contain}。 */
    public static final String MODE_CONTAIN = "contain";

    public Ingredient {
        if (material == null) {
            throw new IllegalArgumentException("missing required field 'material'");
        }
        if (material.isAir()) {
            throw new IllegalArgumentException("'material' must not be air");
        }
    }

    public int amountOrDefault() {
        return amount == null ? DEFAULT_AMOUNT : amount;
    }

    /** 归一化后的匹配模式；不写就是 {@code contain}。 */
    public String mode() {
        return matchMode == null || matchMode.isBlank()
                ? MODE_CONTAIN
                : matchMode.trim().toLowerCase(Locale.ROOT);
    }

    public boolean matchesExactly() {
        return !MODE_MATERIAL.equals(mode()) && !MODE_CONTAIN.equals(mode());
    }

    public boolean matchesOnlyMaterial() {
        return MODE_MATERIAL.equals(mode());
    }

    public boolean containsComponents() {
        return MODE_CONTAIN.equals(mode());
    }

    public void requireSingle(String field, String recipeType) {
        if (amount != null && amount != 1) {
            throw new IllegalArgumentException(
                    "'" + field + ".amount' must be 1 for " + recipeType + " recipes, got " + amount);
        }
    }

    public ItemStack toItemStack(Tingere plugin, String keyPrefix) {
        return ComponentApplyer.apply(plugin, new ItemStack(material, amountOrDefault()), components, keyPrefix);
    }

    /**
     * 注册成原版的配方材料。
     * <p>
     * {@code contain} 没有对应的原版类型——{@code ExactChoice} 走 {@code ItemStack#isSimilar}，要求组件全等，
     * 做不到"可以多、不能少"。所以那一档只能先放宽成 {@code MaterialChoice} 让原版认下材质，
     * 再在准备结果的时候用 {@link #accepts} 补一道判断（见 {@code CraftListener}）。
     */
    public RecipeChoice toRecipeChoice(Tingere plugin, String keyPrefix) {
        return matchesExactly()
                ? new RecipeChoice.ExactChoice(toItemStack(plugin, keyPrefix))
                : new RecipeChoice.MaterialChoice(material);
    }

    /**
     * 把匹配条件编译成可复用的形式。
     * <p>
     * contain 档的复核每次准备配方结果都要跑（{@link #toRecipeChoice} 只能注册成"只认材质"），
     * 现场建物品、导组件太亏，所以在注册时算一次，之后只比 JSON 树。
     */
    public Matcher matcher(Tingere plugin) {
        if (!containsComponents() || components == null || components.isEmpty()) {
            return new Matcher(material, null);
        }
        return new Matcher(material, ComponentSnbt.exportJson(toItemStack(plugin, "match")));
    }

    /**
     * 一条材料的匹配条件快照。
     * <p>
     * {@code required} 是"要求物品带哪些组件"的导出结果；为 null 表示这条材料只认材质。
     * <p>
     * 这里绕开了 {@code ItemStack#getData}——它的签名是 {@code <T> T getData(DataComponentType<T>)}，
     * 而 {@code getDataTypes()} 给的是带通配符的集合，泛型传不进去。改用项目自己的组件导出
     * （key 恰好也是规范化过的写法），比一遍 JSON 树，语义更直白：写的每一项都要对上，
     * 物品上多出来的不管。
     */
    public record Matcher(Material material, JsonNode required) {

        /** 这条材料是否真的对组件有要求；没有的话连物品都不用看。 */
        public boolean hasRequirements() {
            return required != null && !required.isEmpty();
        }

        public boolean accepts(ItemStack item) {
            if (item == null || item.getType().isAir() || item.getType() != material) {
                return false;
            }
            if (!hasRequirements()) {
                return true;
            }
            JsonNode present = ComponentSnbt.exportJson(item);
            for (Iterator<String> fields = required.fieldNames(); fields.hasNext(); ) {
                String field = fields.next();
                if (!required.get(field).equals(present.get(field))) {
                    return false;
                }
            }
            return true;
        }
    }
}

