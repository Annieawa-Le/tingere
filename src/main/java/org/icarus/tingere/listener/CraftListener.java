package org.icarus.tingere.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.config.RecipeLoader;
import org.icarus.tingere.component.ComponentApplyer;
import org.icarus.tingere.nms.ItemRetyper;
import org.icarus.tingere.recipe.Ingredient;
import org.icarus.tingere.recipe.ResultOverride;
import org.icarus.tingere.recipe.SpecialDefinition;

import java.util.List;

/**
 * 在修改这个类之前，你需要知道：
 * <p>
 * 由于沟槽的 Bukkit 限制，不注入 NMS 是绝对无法做到主动定义 transmute 的结果物品的附加标签以及个数的
 * 只能是一个雷霆 Material
 * 因此选择更加不需要脑子的想法接管事件
 * 你不会真的有受虐癖到想去反射 NMS 吧？
 */
@RequiredArgsConstructor
public class CraftListener implements Listener {

    private final Tingere plugin;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed)) {
            return;
        }

        RecipeLoader loader = plugin.getRecipeLoader();
        String fullKey = keyed.getKey().toString();

        // contain 档的原版注册件只能放宽到"只认材质"，组件的部分在这里补判
        if (!satisfiesContain(loader.getIngredientMatchers(fullKey), event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
            return;
        }

        ResultOverride override = loader.getResultOverride(fullKey);

        // Special first
        SpecialDefinition specialInfo = loader.getSpecialRecipeInfo(fullKey);
        if (specialInfo != null) {
            handleSpecialCraft(event, specialInfo, loader.getSpecialSource(fullKey), override);
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
        modified = ComponentApplyer.apply(plugin, modified, override.components(), "result");
        event.getInventory().setResult(modified);
    }

    private void handleSpecialCraft(PrepareItemCraftEvent event,
                                    SpecialDefinition info,
                                    RecipeLoader.SpecialSource source,
                                    ResultOverride override) {
        ItemStack sourceInput = findSourceItem(event.getInventory().getMatrix(), source);
        if (sourceInput == null) {
            return;
        }

        ItemStack finalItem;
        if (info.copyInputOrDefault()) {
            finalItem = ItemRetyper.retype(sourceInput, info.targetMaterial());
        } else {
            finalItem = new ItemStack(info.targetMaterial());
        }
        finalItem.setAmount(override == null ? Ingredient.DEFAULT_AMOUNT : override.amount());

        finalItem = ComponentApplyer.apply(plugin, finalItem, override == null ? null : override.components(), "result");
        finalItem = ComponentApplyer.apply(plugin, finalItem, info.components(), "special");

        // 换材质理论上与附魔无关，掉了就说明中途经过了有损的中间表示，值得立刻知道
        int enchantments = sourceInput.getEnchantments().size();
        if (finalItem.getEnchantments().size() < enchantments) {
            plugin.getLogger().warning("Lost enchantments while retyping " + sourceInput.getType()
                    + " -> " + info.targetMaterial() + " (" + enchantments + " -> "
                    + finalItem.getEnchantments().size() + ")");
        }

        event.getInventory().setResult(finalItem);
    }

    /**
     * 找出网格里那个"要被改造"的物品。
     * <p>
     * 位置与匹配条件都是注册时算好的（见 {@code RecipeLoader#resolveSpecialSource}），这里不能自己算：
     * {@code PrepareItemCraftEvent#getRecipe()} 给的 Bukkit 配方是从 NMS 重建出来的，pattern 里的字符
     * 在重建那一步就丢光了，拿 {@code getChoiceMap()} 去查原始符号永远查不到。
     * <p>
     * 原版的有序配方允许整体平移摆放，注册坐标未必等于玩家实际摆的位置，所以位置对不上时会退化成
     * "在网格里找那个符合源符号的物品"。
     */
    private ItemStack findSourceItem(ItemStack[] matrix, RecipeLoader.SpecialSource source) {
        if (source == null) {
            return null;
        }

        ItemStack atDeclaredSlot = itemAt(matrix, source.slot());
        if (atDeclaredSlot != null && (source.choice() == null || source.choice().test(atDeclaredSlot))) {
            return atDeclaredSlot;
        }

        if (source.choice() != null) {
            for (ItemStack candidate : matrix) {
                if (!isEmpty(candidate) && source.choice().test(candidate)) {
                    return candidate.clone();
                }
            }
        }

        plugin.getLogger().warning("Couldn't locate the source item (slot " + source.slot()
                + ") in the crafting grid; the result will miss its data (enchants, name, ...)");
        return null;
    }

    /**
     * contain 档的复核。原版没有"组件子集"这种匹配（{@code ExactChoice} 是全等），这类材料注册时
     * 只能先放宽成"只认材质"，于是得在这里补一道：网格里必须找得到满足条件的物品。
     * <p>
     * 逐条贪心配对，同一个物品不会被两条材料共用；缺任何一条就把结果清掉，
     * 让玩家看到"摆得不对"而不是拿到一个不该出的产物。
     */
    private boolean satisfiesContain(List<Ingredient.Matcher> matchers, ItemStack[] matrix) {
        if (matchers == null || matchers.isEmpty() || matrix == null) {
            return true;
        }

        boolean[] used = new boolean[matrix.length];
        for (Ingredient.Matcher matcher : matchers) {
            boolean found = false;
            for (int slot = 0; slot < matrix.length; slot++) {
                if (!used[slot] && matcher.accepts(matrix[slot])) {
                    used[slot] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
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

