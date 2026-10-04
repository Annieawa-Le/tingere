package org.icarus.tingere.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmokingRecipe;
import org.icarus.tingere.Tingere;

import java.util.List;
import java.util.Locale;

public record CookingRecipeDefinition(@JsonProperty(required = true) String id,
                                      @JsonProperty(required = true) Kind kind,
                                      @JsonProperty(required = true) Ingredient ingredient,
                                      @JsonProperty(required = true) Ingredient result,
                                      Integer time,
                                      Double experience) implements RecipeDefinition {

    private static final int TICKS_PER_SECOND = 20;

    private static final double DEFAULT_EXPERIENCE = 0.1;

    public enum Kind {
        FURNACE(10),
        BLAST(5),
        SMOKER(5);

        private final int defaultSeconds;

        Kind(int defaultSeconds) {
            this.defaultSeconds = defaultSeconds;
        }

        public int defaultSeconds() {
            return defaultSeconds;
        }

        public static Kind of(String type) {
            return valueOf(type.trim().toUpperCase(Locale.ROOT));
        }
    }

    public CookingRecipeDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("missing required field 'id'");
        }
        if (kind == null) {
            throw new IllegalArgumentException("missing recipe kind");
        }
        if (ingredient == null) {
            throw new IllegalArgumentException("missing required field 'ingredient'");
        }
        if (result == null) {
            throw new IllegalArgumentException("missing required field 'result'");
        }
        ingredient.requireSingle("ingredient", "cooking");
        if (time != null && time <= 0) {
            throw new IllegalArgumentException("'time' must be greater than 0 seconds, got " + time);
        }
        if (experience != null && experience < 0) {
            throw new IllegalArgumentException("'experience' must not be negative, got " + experience);
        }
    }

    public int timeOrDefault() {
        return time == null ? kind.defaultSeconds() : time;
    }

    public int cookingTicks() {
        return timeOrDefault() * TICKS_PER_SECOND;
    }

    public double experienceOrDefault() {
        return experience == null ? DEFAULT_EXPERIENCE : experience;
    }

    @Override
    public Recipe toBukkitRecipe(Tingere plugin) {
        NamespacedKey key = new NamespacedKey(plugin, id);
        ItemStack resultStack = result.toItemStack(plugin);
        RecipeChoice input = ingredient.toRecipeChoice(plugin);
        float exp = (float) experienceOrDefault();
        int ticks = cookingTicks();

        return switch (kind) {
            case FURNACE -> new FurnaceRecipe(key, resultStack, input, exp, ticks);
            case BLAST -> new BlastingRecipe(key, resultStack, input, exp, ticks);
            case SMOKER -> new SmokingRecipe(key, resultStack, input, exp, ticks);
        };
    }

    @Override
    public List<Ingredient> flattenedIngredients() {
        return List.of(ingredient);
    }
}
