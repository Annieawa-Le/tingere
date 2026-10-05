package org.icarus.tingere.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class RecipeEditorView {

    public static final int SIZE = 27;

    /**
     * 信息格 / 九宫格 / 产物格 / 取消 / 保存之外剩下的槽位，一律铺灰玻璃板。
     */
    private static final int[] DECORATION_SLOTS = {
            1, 5, 6, 7, 9, 10, 14, 16, 17, 18, 19, 23, 24, 25
    };

    private RecipeEditorView() {
    }

    public static Inventory create(EditorSession session) {
        EditorHolder holder = new EditorHolder(session);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("配方编辑器"));
        holder.bind(inventory);
        session.setInventory(inventory);
        render(session, inventory);
        return inventory;
    }

    public static void render(EditorSession session, Inventory inventory) {
        ItemStack filler = filler();
        for (int slot : DECORATION_SLOTS) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(EditorSession.INFO_SLOT, infoItem(session));
        inventory.setItem(EditorSession.CANCEL_SLOT, cancelItem(session));
        inventory.setItem(EditorSession.SAVE_SLOT, saveItem(session));
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack infoItem(EditorSession session) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("配方信息", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(line("id", session.getRecipeId().isBlank() ? "（留空自动生成）" : session.getRecipeId()));
        lore.add(line("类型", session.getKind().display() + " (" + session.getKind().id() + ")"));
        lore.add(line("命名空间", session.getNamespace().isBlank() ? "（留空用 default）" : session.getNamespace()));
        lore.add(line("特殊配方", session.isSpecial() ? "是" : "否"));
        if (session.getSpecialTargetMaterial() != null && !session.getSpecialTargetMaterial().isBlank()) {
            lore.add(line("特殊产物材质", session.getSpecialTargetMaterial()));
        }
        if (session.isEditingExisting()) {
            lore.add(line("来源", session.getSourceFile()));
        }
        lore.add(Component.empty());
        lore.add(hint("点击编辑"));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack cancelItem(EditorSession session) {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("取消", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(hint("关闭编辑器，格子里的物品会还给你"));
        if (session.isEditingExisting()) {
            lore.add(hint("对着这里按 Q 删除这条配方"));
        }
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack saveItem(EditorSession session) {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("保存", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(hint("写入 recipes/" + (session.getNamespace().isBlank()
                ? "default" : session.getNamespace()) + ".yml")));
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String label, String value) {
        return Component.text(label + "：", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private static Component hint(String content) {
        return Component.text(content, NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }
}
