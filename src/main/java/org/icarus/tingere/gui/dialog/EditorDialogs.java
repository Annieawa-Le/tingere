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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.gui.EditorSession;
import org.icarus.tingere.gui.MatchMode;
import org.icarus.tingere.gui.RecipeEditorView;
import org.icarus.tingere.gui.RecipeKind;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 编辑器用到的原版对话框
 * <p>
 * 两件要记住的事：弹窗会把箱子界面顶掉，但玩家同时也可能是在往组件屏跳，所以"是不是真走了"
 * 交给 {@code EditorGuiListener#onClose} 延迟一个 tick 去判断；另外关闭对话框不会自动回到编辑器，
 * 每个按钮（含返回）都自带去向。
 */
public final class EditorDialogs {

    private EditorDialogs() {
    }

    /** 配方信息：id / 类型 / 命名空间 / 是否特殊配方。 */
    public static void openRecipeMeta(Player player, EditorSession session) {
        List<DialogInput> inputs = List.of(
                DialogInput.text("recipe_id", Component.text("配方 id"))
                        .initial(session.getRecipeId())
                        .maxLength(64)
                        .build(),
                DialogInput.singleOption("kind", Component.text("配方类型"), List.of(
                                SingleOptionDialogInput.OptionEntry.create(
                                        RecipeKind.SHAPED.id(), Component.text("有序 shaped"),
                                        session.getKind() == RecipeKind.SHAPED),
                                SingleOptionDialogInput.OptionEntry.create(
                                        RecipeKind.SHAPELESS.id(), Component.text("无序 shapeless"),
                                        session.getKind() == RecipeKind.SHAPELESS)))
                        .build(),
                DialogInput.text("namespace", Component.text("命名空间（决定写进哪个文件）"))
                        .initial(session.getNamespace())
                        .maxLength(64)
                        .build(),
                DialogInput.bool("special", Component.text("特殊配方（special）"))
                        .initial(session.isSpecial())
                        .build());

        show(player, session, "配方信息", inputs, response -> {
            session.setRecipeId(orEmpty(response.getText("recipe_id")).trim());
            session.setNamespace(orEmpty(response.getText("namespace")).trim());
            session.setKind(RecipeKind.SHAPELESS.id().equals(response.getText("kind"))
                    ? RecipeKind.SHAPELESS : RecipeKind.SHAPED);
            session.setSpecial(Boolean.TRUE.equals(response.getBoolean("special")));
        });
    }

    /** 材料设置：匹配模式 / 是否作为特殊配方的源材料，另带一个进组件屏的按钮。 */
    public static void openIngredient(Player player, EditorSession session, int gridIndex) {
        EditorSession.IngredientMeta meta = session.metaOf(gridIndex);

        List<DialogInput> inputs = List.of(
                DialogInput.singleOption("match_mode", Component.text("匹配模式"), List.of(
                                SingleOptionDialogInput.OptionEntry.create(
                                        "contain", Component.text("包含：材质对上、带着写的组件，多出来的不管"),
                                        meta.getMatchMode() == MatchMode.CONTAIN),
                                SingleOptionDialogInput.OptionEntry.create(
                                        "exact", Component.text("精确：材质与组件都要对上"),
                                        meta.getMatchMode() == MatchMode.EXACT),
                                SingleOptionDialogInput.OptionEntry.create(
                                        "material", Component.text("仅材质：带附魔或命名的也认"),
                                        meta.getMatchMode() == MatchMode.MATERIAL)))
                        .build(),
                DialogInput.bool("special_source", Component.text("作为特殊配方的源材料（special.source-character）"))
                        .initial(meta.isSpecialSource())
                        .build());

        Consumer<DialogResponseView> apply = response -> {
            meta.setMatchMode(MatchMode.fromYaml(response.getText("match_mode")));
            meta.setSpecialSource(Boolean.TRUE.equals(response.getBoolean("special_source")));
        };

        show(player, session, "材料设置（第 " + (gridIndex + 1) + " 格）", inputs, apply);
    }

    /** 产物设置：数量 / 特殊产物材质 / 是否复制输入数据，另带一个进组件屏的按钮。 */
    public static void openResult(Player player, EditorSession session) {
        Inventory inventory = session.getInventory();
        ItemStack current = inventory == null ? null : inventory.getItem(EditorSession.RESULT_SLOT);
        float amount = current == null || current.getType().isAir() ? 1F : current.getAmount();

        List<DialogInput> inputs = List.of(
                DialogInput.numberRange("amount", Component.text("产物数量"), 1F, 64F)
                        .step(1F)
                        .initial(amount)
                        .build(),
                DialogInput.text("special_target", Component.text("特殊配方产物材质（special.target-material）"))
                        .initial(session.getSpecialTargetMaterial())
                        .maxLength(64)
                        .build(),
                DialogInput.bool("copy_input", Component.text("复制被改造物品的数据（special.copy-input）"))
                        .initial(session.isCopyInput())
                        .build());

        Consumer<DialogResponseView> apply = response -> {
            Float parsed = response.getFloat("amount");
            if (parsed != null && inventory != null) {
                int size = Math.max(1, Math.min(64, Math.round(parsed)));
                ItemStack item = inventory.getItem(EditorSession.RESULT_SLOT);
                if (item != null && !item.getType().isAir()) {
                    item.setAmount(size);
                    inventory.setItem(EditorSession.RESULT_SLOT, item);
                }
            }
            session.setSpecialTargetMaterial(orEmpty(response.getText("special_target")).trim());
            session.setCopyInput(!Boolean.FALSE.equals(response.getBoolean("copy_input")));
        };

        show(player, session, "产物设置", inputs, apply);
    }

    private static void show(Player player, EditorSession session, String title,
                             List<DialogInput> inputs, Consumer<DialogResponseView> onConfirm) {
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.builder(Component.text("确定"))
                .action(DialogAction.customClick((response, audience) -> {
                    onConfirm.accept(response);
                    reopenEditor(player, session);
                }, options()))
                .build());

        List<DialogBody> body = List.of(
                DialogBody.plainMessage(Component.text("填写完点确定；点返回则放弃这次修改")
                        .color(NamedTextColor.GRAY)));

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
                                Component.text("要从 " + file + " 里删掉 '" + id + "' 吗？这个动作不可撤销。")
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

