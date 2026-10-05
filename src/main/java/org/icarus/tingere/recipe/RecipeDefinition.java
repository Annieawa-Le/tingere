package org.icarus.tingere.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.inventory.Recipe;
import org.icarus.tingere.Tingere;

import java.util.List;

/**
 * 古人的智慧之，@NotNull 是写给人看的，你的 Complier 不会有任何错误
 * 这玩意和 @Override 一样是伪代码
 */
public sealed interface RecipeDefinition
        permits ShapedRecipeDefinition,
        ShapelessRecipeDefinition,
        TransmuteRecipeDefinition,
        CookingRecipeDefinition,
        StonecuttingRecipeDefinition,
        SmithingRecipeDefinition {

    String id();
    Ingredient result();
    default SpecialDefinition special() {
        return null;
    }

    Recipe toBukkitRecipe(Tingere plugin, ResolvedRecipe resolved);
    List<Ingredient> flattenedIngredients();
    default JsonNode resultComponents() {
        return result().components();
    }
}
