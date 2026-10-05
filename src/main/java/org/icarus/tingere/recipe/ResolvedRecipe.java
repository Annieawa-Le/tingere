package org.icarus.tingere.recipe;

import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.parser.ParseContext;
import org.icarus.tingere.parser.ParseResult;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一条配方解析一次后的产物
 * 你也不想解析两次吧？
 */
public final class ResolvedRecipe {

    private final ItemStack result;
    private final List<Ingredient> order;
    private final Map<Ingredient, Ingredient.Resolved> byIngredient;

    private ResolvedRecipe(ItemStack result, List<Ingredient> order,
                           Map<Ingredient, Ingredient.Resolved> byIngredient) {
        this.result = result;
        this.order = order;
        this.byIngredient = byIngredient;
    }

    public static ParseResult<ResolvedRecipe> of(Tingere plugin, RecipeDefinition definition, ParseContext ctx) {
        List<Ingredient> flattened = definition.flattenedIngredients();

        Map<Ingredient, Ingredient.Resolved> resolved = new IdentityHashMap<>();
        for (Ingredient ingredient : flattened) {
            if (!resolved.containsKey(ingredient)) {
                resolved.put(ingredient, ingredient.resolve(plugin, ctx).value());
            }
        }
        Ingredient.Resolved result = definition.result().resolve(plugin, ctx).value();
        return ctx.ok(new ResolvedRecipe(result.item(), flattened, resolved));
    }

    public ItemStack result() {
        return this.result;
    }

    public Ingredient.Resolved of(Ingredient ingredient) {
        Ingredient.Resolved value = this.byIngredient.get(ingredient);
        if (value == null) {
            throw new IllegalArgumentException(
                    "ingredient " + ingredient.material() + " is not part of this recipe");
        }
        return value;
    }

    public List<ItemStack> ingredientItems() {
        List<ItemStack> items = new ArrayList<>(this.order.size());
        for (Ingredient ingredient : this.order) {
            items.add(of(ingredient).item());
        }
        return items;
    }

    public List<Ingredient.Matcher> matchers() {
        List<Ingredient.Matcher> matchers = new ArrayList<>();
        for (Ingredient ingredient : this.order) {
            Ingredient.Matcher matcher = of(ingredient).toMatcher();
            if (matcher.hasRequirements()) {
                matchers.add(matcher);
            }
        }
        return matchers;
    }
}
