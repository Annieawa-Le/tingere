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
        lore.add(line("id", session.getRecipeId().isBlank() ? "（自动生成）" : session.getRecipeId()));
        lore.add(line("类型", session.getKind().display() + " · " + session.getKind().id()));
        lore.add(line("文件", targetFile(session)));
        if (session.isSpecial()) {
            String target = session.getSpecialTargetMaterial();
            lore.add(line("special", target == null || target.isBlank() ? "已开启" : target));
        }
        lore.add(hint("右键已放置材料修改匹配模式等信息"));
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
        lore.add(hint("左键：关闭并退回所有物品"));
        if (session.isEditingExisting()) {
            lore.add(hint("Q：删除此配方"));
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
        meta.lore(List.of(hint("左键：写入 " + targetFile(session))));
        item.setItemMeta(meta);
        return item;
    }

    private static String targetFile(EditorSession session) {
        if (session.isEditingExisting() && session.getSourceFile() != null) {
            return session.getSourceFile();
        }
        String namespace = session.getNamespace() == null || session.getNamespace().isBlank()
                ? "default" : session.getNamespace().trim();
        return namespace + ".yml";
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
