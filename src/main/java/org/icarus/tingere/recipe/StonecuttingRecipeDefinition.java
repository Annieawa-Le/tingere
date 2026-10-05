package org.icarus.tingere.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.icarus.tingere.Tingere;

import java.util.List;

public record StonecuttingRecipeDefinition(@JsonProperty(required = true) String id,
                                           @JsonProperty(required = true) Ingredient ingredient,
                                           @JsonProperty(required = true) Ingredient result)
        implements RecipeDefinition {

    public StonecuttingRecipeDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("missing required field 'id'");
        }
        if (ingredient == null) {
            throw new IllegalArgumentException("missing required field 'ingredient'");
        }
        if (result == null) {
            throw new IllegalArgumentException("missing required field 'result'");
        }
        ingredient.requireSingle("ingredient", "stonecutter");
    }

    @Override
    public Recipe toBukkitRecipe(Tingere plugin, ResolvedRecipe resolved) {
        return new StonecuttingRecipe(
                new NamespacedKey(plugin, id),
                resolved.result(),
                ingredient.toRecipeChoice(resolved.of(ingredient)));
    }

    @Override
    public List<Ingredient> flattenedIngredients() {
        return List.of(ingredient);
    }
}
