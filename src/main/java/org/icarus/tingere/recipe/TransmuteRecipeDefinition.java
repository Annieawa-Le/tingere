package org.icarus.tingere.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.TransmuteRecipe;
import org.icarus.tingere.Tingere;

import java.util.List;

public record TransmuteRecipeDefinition(@JsonProperty(required = true) String id,
                                        @JsonProperty(required = true) Ingredient input,
                                        @JsonProperty(required = true) Ingredient material,
                                        @JsonProperty(required = true) Ingredient result,
                                        SpecialDefinition special) implements RecipeDefinition {

    public TransmuteRecipeDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("missing required field 'id'");
        }
        if (input == null) {
            throw new IllegalArgumentException("missing required field 'input'");
        }
        if (material == null) {
            throw new IllegalArgumentException("missing required field 'material'");
        }
        if (result == null) {
            throw new IllegalArgumentException("missing required field 'result'");
        }
    }

    @Override
    public Recipe toBukkitRecipe(Tingere plugin, ResolvedRecipe resolved) {
        return new TransmuteRecipe(
                new NamespacedKey(plugin, id),
                result.material(),
                new RecipeChoice.MaterialChoice(input.material()),
                new RecipeChoice.ExactChoice(resolved.of(material).item()));
    }

    @Override
    public List<Ingredient> flattenedIngredients() {
        return List.of(input, material);
    }
}
