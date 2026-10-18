package org.icarus.tingere.gui.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.gui.EditorSession;
import org.icarus.tingere.gui.MatchMode;
import org.icarus.tingere.gui.RecipeEditorView;
import org.icarus.tingere.gui.RecipeKind;
import org.icarus.tingere.recipe.Ingredient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 你的信息交互写的很地狱
 */
@SuppressWarnings("UnstableApiUsage")
public final class EditorDialogs {

    private EditorDialogs() {
    }

    public static void openRecipeMeta(Player player, EditorSession session) {
        List<DialogInput> inputs = List.of(
                DialogInput.text("recipe_id", Component.text("配方 id"))
                        .initial(session.getRecipeId())
                        .maxLength(64)
                        .build(),
                DialogInput.singleOption("kind", Component.text("配方类型"), List.of(
                                SingleOptionDialogInput.OptionEntry.create(
                                        RecipeKind.SHAPED.id(), Component.text("有序"),
                                        session.getKind() == RecipeKind.SHAPED),
                                SingleOptionDialogInput.OptionEntry.create(
                                        RecipeKind.SHAPELESS.id(), Component.text("无序"),
                                        session.getKind() == RecipeKind.SHAPELESS)))
                        .build(),
                DialogInput.text("namespace", Component.text("命名空间"))
                        .initial(session.getNamespace())
                        .maxLength(64)
                        .build(),
                DialogInput.bool("special", Component.text("特殊配方"))
                        .initial(session.isSpecial())
                        .build());

        show(player, session, "配方信息", "点击确定以保存", inputs, response -> {
            session.setRecipeId(orEmpty(response.getText("recipe_id")).trim());
            session.setNamespace(orEmpty(response.getText("namespace")).trim());
            session.setKind(RecipeKind.SHAPELESS.id().equals(response.getText("kind"))
                    ? RecipeKind.SHAPELESS : RecipeKind.SHAPED);
            session.setSpecial(Boolean.TRUE.equals(response.getBoolean("special")));
        });
    }

    public static void openIngredient(Player player, EditorSession session, int gridIndex) {
        EditorSession.IngredientMeta meta = session.metaOf(gridIndex);

        List<DialogInput> inputs = List.of(
                DialogInput.singleOption("match_mode", Component.text("匹配模式"), List.of(
                                SingleOptionDialogInput.OptionEntry.create(
                                        "contain", Component.text("包含"),
                                        meta.getMatchMode() == MatchMode.CONTAIN),
                                SingleOptionDialogInput.OptionEntry.create(
                                        "exact", Component.text("完全一致"),
                                        meta.getMatchMode() == MatchMode.EXACT),
                                SingleOptionDialogInput.OptionEntry.create(
                                        "material", Component.text("仅材质"),
                                        meta.getMatchMode() == MatchMode.MATERIAL)))
                        .build(),
                DialogInput.bool("special_source", Component.text("设为特殊配方源物品"))
                        .initial(meta.isSpecialSource())
                        .build());

        Consumer<DialogResponseView> apply = response -> {
            meta.setMatchMode(MatchMode.fromYaml(response.getText("match_mode")));
            meta.setSpecialSource(Boolean.TRUE.equals(response.getBoolean("special_source")));
        };

        show(player, session, "材料 - 第 " + (gridIndex + 1) + " 格",
                "包含：材质与组件 | 精确：组件完全一致 | 仅材质：忽略其它组件",
                inputs, apply);
    }

    public static void openResult(Player player, EditorSession session) {
        Inventory inventory = session.getInventory();
        ItemStack current = inventory == null ? null : inventory.getItem(EditorSession.RESULT_SLOT);
        int maxAmount = maxStackSize(current);
        float amount = current == null || current.getType().isAir() ? 1F : current.getAmount();

        List<DialogInput> inputs = List.of(
                DialogInput.numberRange("amount", Component.text("产物数量"), 1F, maxAmount)
                        .step(1F)
                        .initial(amount)
                        .build(),
                DialogInput.text("special_target", Component.text("特殊配方产物材质"))
                        .initial(session.getSpecialTargetMaterial())
                        .maxLength(64)
                        .build(),
                DialogInput.bool("copy_input", Component.text("复制源物品组件"))
                        .initial(session.isCopyInput())
                        .build());

        Consumer<DialogResponseView> apply = response -> {
            Float parsed = response.getFloat("amount");
            if (parsed != null && inventory != null) {
                int size = Math.clamp(Math.round(parsed), 1, maxAmount);
                ItemStack item = inventory.getItem(EditorSession.RESULT_SLOT);
                if (item != null && !item.getType().isAir()) {
                    item.setAmount(size);
                    inventory.setItem(EditorSession.RESULT_SLOT, item);
                }
            }
            session.setSpecialTargetMaterial(orEmpty(response.getText("special_target")).trim());
            session.setCopyInput(!Boolean.FALSE.equals(response.getBoolean("copy_input")));
        };

        show(player, session, "产物设置",
                "数量上限跟随 max_stack_size 组件",
                inputs, apply);
    }

    private static int maxStackSize(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Ingredient.MAX_AMOUNT;
        }
        try {
            var handle = CraftItemStack.asNMSCopy(item);
            if (handle != null) {
                Integer configured = handle.get(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE);
                if (configured != null) {
                    return Math.clamp(configured, 1, Ingredient.MAX_COMPONENT_AMOUNT);
                }
            }
        } catch (RuntimeException ignored) {
        }
        return Ingredient.MAX_AMOUNT;
    }

    private static void show(Player player, EditorSession session, String title, String bodyText,
                             List<DialogInput> inputs, Consumer<DialogResponseView> onConfirm) {
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.builder(Component.text("确定"))
                .action(DialogAction.customClick((response, audience) -> {
                    onConfirm.accept(response);
                    reopenEditor(player, session);
                }, options()))
                .build());

        List<DialogBody> body = List.of(
                DialogBody.plainMessage(Component.text(bodyText).color(NamedTextColor.GRAY)));

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(title))
                        .canCloseWithEscape(false)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(buttons)
                        .exitAction(ActionButton.builder(Component.text("返回"))
                                .action(DialogAction.customClick((response, audience) ->
                                        reopenEditor(player, session), options()))
                                .build())
                        .build()));

        player.showDialog(dialog);
    }

    private static void reopenEditor(Player player, EditorSession session) {
        Inventory inventory = session.getInventory();
        if (inventory == null || !player.isOnline()) {
            return;
        }
        RecipeEditorView.render(session, inventory);
        player.openInventory(inventory);
    }

    public static void openDeleteConfirm(Player player, EditorSession session, Runnable onDelete) {
        String file = session.getSourceFile() == null ? "配方文件" : session.getSourceFile();
        String id = session.getSourceRecipeId() == null ? "" : session.getSourceRecipeId();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("删除配方"))
                        .canCloseWithEscape(false)
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("删除 " + file + " 里的 '" + id + "'？此操作不可撤销！")
                                        .color(NamedTextColor.RED))))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text("删除"))
                                .action(DialogAction.customClick((response, audience) -> {
                                    onDelete.run();
                                    reopenEditor(player, session);
                                }, options()))
                                .build(),
                        ActionButton.builder(Component.text("取消"))
                                .action(DialogAction.customClick((response, audience) ->
                                        reopenEditor(player, session), options()))
                                .build())));

        player.showDialog(dialog);
    }

    private static ClickCallback.Options options() {
        return ClickCallback.Options.builder()
                .uses(1)
                .lifetime(Duration.ofMinutes(30))
                .build();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
