package org.icarus.tingere.gui;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 一次配方编辑的会话状态。
 * <p>
 * 物品本身不放这里..：九宫格与产物格用的是 GUI 里的真实 {@code ItemStack}，
 */
@Getter
@Setter
public class EditorSession {

    /** 九宫格在 GUI 里的槽位，行优先，顺序与配方 pattern 的 3x3 一致。 */
    public static final int[] GRID_SLOTS = {2, 3, 4, 11, 12, 13, 20, 21, 22};

    public static final int INFO_SLOT = 0;
    public static final int RESULT_SLOT = 15;
    public static final int CANCEL_SLOT = 8;
    public static final int SAVE_SLOT = 26;

    private final UUID owner;

    @Setter
    private String recipeId = "";
    private String namespace = "";
    private RecipeKind kind = RecipeKind.SHAPED;
    private boolean special;
    private String specialTargetMaterial = "";
    private boolean copyInput = true;

    /** 编辑已有配方时的来源文件名（相对 recipes/）与原始 id；新建时都是 null。 */
    private String sourceFile;
    private String sourceRecipeId;

    private final Map<Integer, IngredientMeta> ingredientMeta = new HashMap<>();

    /** 界面本体；对话框回调后要靠它把编辑器顶回来。 */
    private Inventory inventory;

    public EditorSession(UUID owner) {
        this.owner = owner;
    }

    public boolean isEditingExisting() {
        return sourceRecipeId != null;
    }

    public IngredientMeta metaOf(int gridIndex) {
        return ingredientMeta.computeIfAbsent(gridIndex, index -> new IngredientMeta());
    }

    public static int gridIndex(int rawSlot) {
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (GRID_SLOTS[i] == rawSlot) {
                return i;
            }
        }
        return -1;
    }

    @Getter
    @Setter
    public static class IngredientMeta {
        private MatchMode matchMode = MatchMode.CONTAIN;
        private boolean specialSource;
    }
}
