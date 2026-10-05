package org.icarus.tingere.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.icarus.tingere.Tingere;

import java.util.List;

public record SmithingRecipeDefinition(@JsonProperty(required = true) String id,
                                       @JsonProperty(required = true) Ingredient ingredient,
                                       @JsonProperty(required = true) Ingredient template,
                                       @JsonProperty(required = true) Ingredient addition,
                                       @JsonProperty(required = true) Ingredient result)
        implements RecipeDefinition {

    public SmithingRecipeDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("missing required field 'id'");
        }
        if (ingredient == null) {
            throw new IllegalArgumentException("missing required field 'ingredient'");
        }
        if (template == null) {
            throw new IllegalArgumentException("missing required field 'template'");
        }
        if (addition == null) {
            throw new IllegalArgumentException("missing required field 'addition'");
        }
        if (result == null) {
            throw new IllegalArgumentException("missing required field 'result'");
        }
        ingredient.requireSingle("ingredient", "smithing");
        template.requireSingle("template", "smithing");
        addition.requireSingle("addition", "smithing");
        result.requireSingle("result", "smithing");
    }

    @Override
    public Recipe toBukkitRecipe(Tingere plugin, ResolvedRecipe resolved) {
        return new SmithingTransformRecipe(
                new NamespacedKey(plugin, id),
                resolved.result(),
                template.toRecipeChoice(resolved.of(template)),
                ingredient.toRecipeChoice(resolved.of(ingredient)),
                addition.toRecipeChoice(resolved.of(addition)));
    }
    
    @Override
    public List<Ingredient> flattenedIngredients() {
        return List.of(template, ingredient, addition);
    }
}
