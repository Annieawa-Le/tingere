package org.icarus.tingere.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.config.RecipeWriter;
import org.icarus.tingere.gui.EditorHolder;
import org.icarus.tingere.gui.EditorSession;
import org.icarus.tingere.gui.RecipeEditorView;
import org.icarus.tingere.gui.dialog.EditorDialogs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.IOException;
import java.util.logging.Level;

public class EditorGuiListener implements Listener {

    private final Tingere plugin;
    private final RecipeWriter writer;

    public EditorGuiListener(Tingere plugin) {
        this.plugin = plugin;
        this.writer = new RecipeWriter(plugin);
    }

    // ------------------------------------------------------------------ 编辑器界面

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EditorHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // 点的是玩家自己的背包：只放行普通点击，shift 点击会把东西塞进装饰格
        if (event.getClickedInventory() != top) {
            if (event.isShiftClick() || event.getClick() == ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
            }
            return;
        }

        EditorSession session = holder.getSession();
        int slot = event.getRawSlot();

        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            event.setCancelled(true);
            if (slot == EditorSession.CANCEL_SLOT && session.isEditingExisting()) {
                EditorDialogs.openDeleteConfirm(player, session, () -> delete(player, session));
            }
            return;
        }

        if (slot == EditorSession.INFO_SLOT) {
            event.setCancelled(true);
            EditorDialogs.openRecipeMeta(player, session);
            return;
        }

        if (slot == EditorSession.CANCEL_SLOT) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        if (slot == EditorSession.SAVE_SLOT) {
            event.setCancelled(true);
            save(player, session);
            return;
        }

        int gridIndex = EditorSession.gridIndex(slot);
        if (gridIndex >= 0) {
            ItemStack current = top.getItem(slot);
            if (current == null || current.getType().isAir()) {
                return;
            }
            if (event.getClick() == ClickType.RIGHT) {
                return;
            }
            event.setCancelled(true);
            EditorDialogs.openIngredient(player, session, gridIndex);
            return;
        }

        if (slot == EditorSession.RESULT_SLOT) {
            ItemStack current = top.getItem(slot);
            if (current == null || current.getType().isAir()) {
                return;
            }
            event.setCancelled(true);
            EditorDialogs.openResult(player, session);
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EditorHolder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot >= top.getSize()) {
                continue;
            }
            if (!isEditable(slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EditorHolder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            // 弹对话框不会真的关掉这个箱子，所以服务端眼里它还开着；玩家回到原位就什么都不做
            if (player.getOpenInventory().getTopInventory() == top) {
                return;
            }
            for (int slot : EditorSession.GRID_SLOTS) {
                returnItem(player, top, slot);
            }
            returnItem(player, top, EditorSession.RESULT_SLOT);
            holder.getSession().setInventory(null);
        }, 1L);
    }

    // ------------------------------------------------------------------ 工具

    private void save(Player player, EditorSession session) {
        Inventory editor = session.getInventory();
        if (editor == null) {
            return;
        }
        try {
            String file = writer.save(session, editor);
            plugin.getRecipeLoader().reloadAll();
            player.sendRichMessage("<green>已保存到 recipes/" + file);
            player.sendActionBar(Component.text("已保存 recipes/" + file, NamedTextColor.GREEN));
            RecipeEditorView.render(session, editor);
            player.openInventory(editor);
        } catch (IllegalArgumentException e) {
            player.sendRichMessage("<red>" + e.getMessage());
            player.sendActionBar(Component.text(e.getMessage(), NamedTextColor.RED));
        } catch (IOException e) {
            String message = "写入失败：" + e.getMessage();
            player.sendRichMessage("<red>" + message);
            player.sendActionBar(Component.text(message, NamedTextColor.RED));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "保存配方时出错", e);
            player.sendActionBar(Component.text("保存失败，详情见控制台", NamedTextColor.RED));
        }
    }

    private void delete(Player player, EditorSession session) {
        if (!session.isEditingExisting()) {
            return;
        }
        String id = session.getSourceRecipeId();
        try {
            boolean removed = writer.delete(session);
            plugin.getRecipeLoader().reloadAll();
            if (removed) {
                player.sendRichMessage("<green>已删除配方 '" + id + "'");
            }
            session.setSourceRecipeId(null);
            session.setSourceFile(null);
            player.closeInventory();
        } catch (IOException e) {
            player.sendRichMessage("<red>删除失败：" + e.getMessage());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "删除配方时出错", e);
            player.sendRichMessage("<red>删除失败，详情见控制台");
        }
    }

    private static boolean isEditable(int slot) {
        return slot == EditorSession.RESULT_SLOT || EditorSession.gridIndex(slot) >= 0;
    }

    private static void returnItem(Player player, Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType().isAir()) {
            return;
        }
        inventory.setItem(slot, null);
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}


