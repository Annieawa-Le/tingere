package org.icarus.tingere.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponentPatch;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.component.ComponentApplier;
import org.icarus.tingere.nms.ComponentNbt;
import org.icarus.tingere.parser.ParseContext;
import org.icarus.tingere.parser.ParseResult;

import java.util.Locale;

public record Ingredient(@JsonProperty(required = true) Material material,
                         Integer amount,
                         String matchMode,
                         JsonNode components) {

    public static final int DEFAULT_AMOUNT = 1;
    public static final int MAX_AMOUNT = net.minecraft.world.item.Item.DEFAULT_MAX_STACK_SIZE;
    public static final int MAX_COMPONENT_AMOUNT = net.minecraft.world.item.Item.ABSOLUTE_MAX_STACK_SIZE;

    private static final String[] MAX_STACK_FIELDS = {
            "max_stack_size", "max-stack-size", "max_stack", "max-stack",
            "stack_size", "stack-size", "minecraft:max_stack_size", "minecraft:max-stack-size"
    };

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
        int amountLimit = maxAmount(components);
        if (amount != null && (amount < 1 || amount > amountLimit)) {
            throw new IllegalArgumentException(
                    "'amount' must be between 1 and " + amountLimit + ", got " + amount);
        }
        if (matchMode != null && !matchMode.isBlank()) {
            String normalized = matchMode.trim().toLowerCase(Locale.ROOT);
            if (!MODE_MATERIAL.equals(normalized) && !MODE_CONTAIN.equals(normalized)
                    && !MODE_EXACT.equals(normalized)) {
                throw new IllegalArgumentException(
                        "'match-mode' must be one of material, contain, exact, got '" + matchMode + "'");
            }
        }
    }

    public static int maxAmount(JsonNode components) {
        if (components != null && components.isObject()) {
            for (String field : MAX_STACK_FIELDS) {
                JsonNode value = components.get(field);
                if (value == null || value.isNull()) {
                    continue;
                }
                if (value.isNumber() || value.isTextual()) {
                    int limit = value.asInt();
                    if (limit >= 1) {
                        return Math.min(limit, MAX_COMPONENT_AMOUNT);
                    }
                }
            }
        }
        return MAX_AMOUNT;
    }

    public int amountOrDefault() {
        return amount == null ? DEFAULT_AMOUNT : amount;
    }

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

    /**
     * 芷沐小姐你知道吗你在你不知情的情况下写出了两次 parse
     */
    public ParseResult<Resolved> resolve(Tingere plugin, ParseContext ctx) {
        ParseResult<ComponentApplier.Parsed> parsed = ComponentApplier.parse(plugin, components, ctx);
        ItemStack base = new ItemStack(material, amountOrDefault());
        ItemStack item = ComponentApplier.of(plugin).apply(base, parsed.value(), components);
        return parsed.map(value -> new Resolved(material, item, predicate(value.patch()), value));
    }

    private DataComponentExactPredicate predicate(DataComponentPatch patch) {
        if (!containsComponents() || patch.isEmpty()) {
            return null;
        }
        return ComponentNbt.exactPredicate(patch);
    }

    public record Resolved(Material material, ItemStack item, DataComponentExactPredicate predicate,
                           ComponentApplier.Parsed parsed) {

        public Matcher toMatcher() {
            return new Matcher(material, predicate);
        }
    }

    public ItemStack toItemStack(Tingere plugin) {
        return resolve(plugin, new ParseContext(null, null)).orElseThrow().item();
    }

    public RecipeChoice toRecipeChoice(Resolved resolved) {
        return matchesExactly()
                ? new RecipeChoice.ExactChoice(resolved.item())
                : new RecipeChoice.MaterialChoice(material);
    }

    public RecipeChoice toStrictRecipeChoice(Resolved resolved) {
        if (matchesOnlyMaterial() || components == null || components.isEmpty()) {
            return new RecipeChoice.MaterialChoice(material);
        }
        return new RecipeChoice.ExactChoice(resolved.item());
    }

    public record Matcher(Material material, DataComponentExactPredicate required) {

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
            net.minecraft.world.item.ItemStack handle = CraftItemStack.asNMSCopy(item);
            return handle != null && required.test(handle);
        }
    }
}

