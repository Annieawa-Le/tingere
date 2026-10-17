package org.icarus.tingere.listener;

import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.component.ComponentApplier;
import org.icarus.tingere.config.RecipeLoader;
import org.icarus.tingere.nms.ItemRetyper;
import org.icarus.tingere.recipe.Ingredient;
import org.icarus.tingere.recipe.ResultOverride;
import org.icarus.tingere.recipe.SpecialDefinition;

import java.util.List;
import java.util.logging.Logger;

/**
 * 在修改这个类之前，你需要知道：
 * <p>
 * 由于沟槽的 Bukkit 限制，不注入 NMS 是绝对无法做到主动定义 transmute 的结果物品的附加标签以及个数的
 * 只能是一个雷霆 Material
 * 因此选择更加不需要脑子的想法接管事件
 * 你不会真的有受虐癖到想去反射 NMS 吧？
 */
public class CraftListener implements Listener {

    private final Tingere plugin;
    private final Logger logger;
    
    public CraftListener(Tingere plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        try {
            applyRecipe(event);
        } catch (RuntimeException e) {
            logger.warning("Failed to build the craft result: " + e.getMessage());
            event.getInventory().setResult(null);
        }
    }

    private void applyRecipe(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed)) {
            return;
        }

        RecipeLoader loader = plugin.getRecipeLoader();
        String fullKey = keyed.getKey().toString();

        if (!satisfiesContain(loader.getIngredientMatchers(fullKey), event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
            return;
        }

        ResultOverride override = loader.getResultOverride(fullKey);

        // Special first
        SpecialDefinition specialInfo = loader.getSpecialRecipeInfo(fullKey);
        if (specialInfo != null) {
            handleSpecialCraft(event, specialInfo, loader.getSpecialSource(fullKey),
                    loader.getSpecialParsed(fullKey), override);
            return;
        }

        if (override == null) {
            return;
        }

        ItemStack originalResult = event.getInventory().getResult();
        if (isEmpty(originalResult)) {
            return;
        }

        ItemStack modified = originalResult.clone();
        if (override.amount() > 0) {
            modified.setAmount(override.amount());
        }
        modified = ComponentApplier.of(plugin).apply(modified, override.parsed(), override.components());
        event.getInventory().setResult(modified);
    }

    private void handleSpecialCraft(PrepareItemCraftEvent event,
                                    SpecialDefinition info,
                                    RecipeLoader.SpecialSource source,
                                    ComponentApplier.Parsed specialParsed,
                                    ResultOverride override) {
        boolean copyInput = info.copyInputOrDefault();
        ItemStack sourceInput = copyInput ? findSourceItem(event.getInventory().getMatrix(), source) : null;
        if (copyInput && sourceInput == null) {
            logger.warning("Couldn't locate the source item for special recipe; hiding the result.");
            event.getInventory().setResult(null);
            return;
        }

        ItemStack finalItem;
        if (copyInput) {
            finalItem = ItemRetyper.retype(sourceInput, info.targetMaterial());
        } else {
            finalItem = new ItemStack(info.targetMaterial());
        }
        finalItem.setAmount(override == null ? Ingredient.DEFAULT_AMOUNT : override.amount());

        if (override != null) {
            finalItem = ComponentApplier.of(plugin)
                    .apply(finalItem, override.parsed(), override.components());
        }
        if (specialParsed != null) {
            finalItem = ComponentApplier.of(plugin)
                    .apply(finalItem, specialParsed, info.components());
        } else {
            finalItem = ComponentApplier.apply(plugin, finalItem, info.components());
        }

        if (copyInput) {
            int enchantments = sourceInput.getEnchantments().size();
            if (finalItem.getEnchantments().size() < enchantments) {
                logger.warning("Lost enchantments while retyping " + sourceInput.getType()
                        + " -> " + info.targetMaterial() + " (" + enchantments + " -> "
                        + finalItem.getEnchantments().size() + ")");
            }
        }

        event.getInventory().setResult(finalItem);
    }

    private ItemStack findSourceItem(ItemStack[] matrix, RecipeLoader.SpecialSource source) {
        if (source == null) {
            return null;
        }

        ItemStack atDeclaredSlot = itemAt(matrix, source.slot());
        if (atDeclaredSlot != null && accepts(source, atDeclaredSlot)) {
            return atDeclaredSlot;
        }

        for (ItemStack candidate : matrix) {
            if (accepts(source, candidate)) {
                return candidate.clone();
            }
        }

        if (source.slot() < 0) {
            for (ItemStack candidate : matrix) {
                if (!isEmpty(candidate)) {
                    return candidate.clone();
                }
            }
        }

        logger.warning("Couldn't locate the source item (slot " + source.slot()
                + ") in the crafting grid; the result will miss its data (enchants, name, ...)");
        return null;
    }

    private static boolean accepts(RecipeLoader.SpecialSource source, ItemStack item) {
        if (isEmpty(item)) {
            return false;
        }
        if (source.choice() != null && !source.choice().test(item)) {
            return false;
        }
        return source.matcher() == null || source.matcher().accepts(item);
    }

    private boolean satisfiesContain(List<Ingredient.Matcher> matchers, ItemStack[] matrix) {
        if (matchers == null || matchers.isEmpty() || matrix == null) {
            return true;
        }
        return assignMatcher(matchers, 0, matrix, new boolean[matrix.length]);
    }

    private static boolean assignMatcher(List<Ingredient.Matcher> matchers, int index,
                                         ItemStack[] matrix, boolean[] used) {
        if (index == matchers.size()) {
            return true;
        }
        Ingredient.Matcher matcher = matchers.get(index);
        for (int slot = 0; slot < matrix.length; slot++) {
            if (!used[slot] && matcher.accepts(matrix[slot])) {
                used[slot] = true;
                if (assignMatcher(matchers, index + 1, matrix, used)) {
                    return true;
                }
                used[slot] = false;
            }
        }
        return false;
    }

    private static ItemStack itemAt(ItemStack[] matrix, int slot) {
        if (slot < 0 || slot >= matrix.length) {
            return null;
        }
        ItemStack item = matrix[slot];
        return isEmpty(item) ? null : item.clone();
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}

