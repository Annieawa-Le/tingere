package org.icarus.tingere.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import net.minecraft.core.component.DataComponentExactPredicate;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.component.ComponentApplyer;
import org.icarus.tingere.component.ComponentDialect;
import org.icarus.tingere.nms.ComponentNbt;

import java.util.Locale;

public record Ingredient(@JsonProperty(required = true) Material material,
                         Integer amount,
                         String matchMode,
                         JsonNode components) {

    public static final int DEFAULT_AMOUNT = 1;
    public static final String MODE_MATERIAL = "material";
    public static final String MODE_CONTAIN = "contain";
    public static final String MODE_EXACT = "exact";

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

    // 修改后默认 contain 模式匹配
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

    public org.bukkit.inventory.ItemStack toItemStack(Tingere plugin) {
        return ComponentApplyer.apply(plugin, new org.bukkit.inventory.ItemStack(material, amountOrDefault()), components);
    }

    /**
     * 注册成原版的配方材料。
     */
    public RecipeChoice toRecipeChoice(Tingere plugin) {
        return matchesExactly()
                ? new RecipeChoice.ExactChoice(toItemStack(plugin))
                : new RecipeChoice.MaterialChoice(material);
    }

    public Matcher matcher(Tingere plugin) {
        if (!containsComponents() || components == null || components.isEmpty()) {
            return new Matcher(material, null);
        }
        return new Matcher(material, ComponentNbt.exactPredicate(ComponentDialect.toVanilla(components)));
    }

    public record Matcher(Material material, DataComponentExactPredicate required) {

        public boolean hasRequirements() {
            return required != null && !required.isEmpty();
        }

        public boolean accepts(org.bukkit.inventory.ItemStack item) {
            if (item == null || item.getType().isAir() || item.getType() != material) {
                return false;
            }
            if (!hasRequirements()) {
                return true;
            }
            net.minecraft.world.item.ItemStack handle = CraftItemStack.asNMSCopy(item);
            return handle != null && required.test(handle);
        }
    }
}

