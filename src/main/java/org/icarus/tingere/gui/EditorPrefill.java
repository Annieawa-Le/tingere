package org.icarus.tingere.gui;

import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.parser.RecipeParser;
import org.icarus.tingere.recipe.Ingredient;
import org.icarus.tingere.recipe.RecipeDefinition;
import org.icarus.tingere.recipe.ShapedRecipeDefinition;
import org.icarus.tingere.recipe.ShapelessRecipeDefinition;
import org.icarus.tingere.recipe.SpecialDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class EditorPrefill {

    private static final RecipeParser PARSER = new RecipeParser();

    private EditorPrefill() {
    }

    public static void fill(Tingere plugin, EditorSession session, String relativeFile, String recipeId)
            throws IOException {
        JsonNode root = PARSER.read(recipePath(plugin, relativeFile));
        JsonNode node = root.has("type") ? root : root.get(recipeId);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("文件 " + relativeFile + " 里没有配方 '" + recipeId + "'");
        }

        RecipeDefinition definition = PARSER.bind(node);
        if (definition instanceof ShapedRecipeDefinition shaped) {
            session.setKind(RecipeKind.SHAPED);
            fillShaped(plugin, session, shaped);
        } else if (definition instanceof ShapelessRecipeDefinition shapeless) {
            session.setKind(RecipeKind.SHAPELESS);
            fillShapeless(plugin, session, shapeless);
        } else {
            throw new IllegalArgumentException("编辑器只支持 shaped 与 shapeless，这条是 "
                    + definition.getClass().getSimpleName());
        }

        Inventory inventory = session.getInventory();
        inventory.setItem(EditorSession.RESULT_SLOT,
                definition.result().toItemStack(plugin));

        SpecialDefinition special = definition.special();
        session.setRecipeId(recipeId);
        session.setNamespace(stripExtension(relativeFile));
        session.setSpecial(special != null);
        if (special != null) {
            session.setSpecialTargetMaterial(special.targetMaterial().name());
            session.setCopyInput(special.copyInputOrDefault());
        }
        session.setSourceFile(relativeFile);
        session.setSourceRecipeId(recipeId);
    }

    private static void fillShaped(Tingere plugin, EditorSession session, ShapedRecipeDefinition shaped) {
        List<String> pattern = shaped.pattern();
        Map<Character, Ingredient> ingredients = shaped.ingredients();

        for (int row = 0; row < Math.min(pattern.size(), 3); row++) {
            String line = pattern.get(row);
            for (int col = 0; col < Math.min(line.length(), 3); col++) {
                char symbol = line.charAt(col);
                if (symbol == ' ') {
                    continue;
                }
                Ingredient ingredient = ingredients.get(symbol);
                if (ingredient != null) {
                    place(plugin, session, row * 3 + col, ingredient, symbol, shaped.special());
                }
            }
        }
    }

    private static void fillShapeless(Tingere plugin, EditorSession session, ShapelessRecipeDefinition shapeless) {
        List<Ingredient> ingredients = shapeless.ingredients();
        for (int i = 0; i < Math.min(ingredients.size(), EditorSession.GRID_SLOTS.length); i++) {
            place(plugin, session, i, ingredients.get(i), null, shapeless.special());
        }
    }

    private static void place(Tingere plugin, EditorSession session, int gridIndex, Ingredient ingredient,
                              Character symbol, SpecialDefinition special) {
        ItemStack item = ingredient.toItemStack(plugin);
        session.getInventory().setItem(EditorSession.GRID_SLOTS[gridIndex], item);

        EditorSession.IngredientMeta meta = session.metaOf(gridIndex);
        meta.setMatchMode(modeOf(ingredient));
        meta.setSpecialSource(isSpecialSource(symbol, gridIndex, special));
    }

    private static MatchMode modeOf(Ingredient ingredient) {
        if (ingredient.matchesOnlyMaterial()) {
            return MatchMode.MATERIAL;
        }
        return ingredient.containsComponents() ? MatchMode.CONTAIN : MatchMode.EXACT;
    }

    private static boolean isSpecialSource(Character symbol, int gridIndex, SpecialDefinition special) {
        if (special == null) {
            return false;
        }
        if (special.sourceSlot() != null) {
            return special.sourceSlot() == gridIndex;
        }
        return symbol != null && symbol.toString().equals(special.sourceCharacter());
    }

    private static Path recipePath(Tingere plugin, String relativeFile) {
        return plugin.getDataFolder().toPath().resolve("recipes").resolve(relativeFile);
    }

    private static String stripExtension(String relativeFile) {
        return relativeFile.replaceFirst("(?i)\\.ya?ml$", "");
    }
}
