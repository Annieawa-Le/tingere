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
import java.nio.file.NoSuchFileException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EditorGuiListener implements Listener {

    private final Tingere plugin;
    private final RecipeWriter writer;
    private final Logger logger;

    public EditorGuiListener(Tingere plugin) {
        this.plugin = plugin;
        this.writer = new RecipeWriter(plugin);
        this.logger = plugin.getLogger();
    }

    /**
     * 你不觉得右键才能拿物品是一件很诡异的事情吗
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EditorHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() != top) {
            if (event.getClick() == ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack moving = event.getCurrentItem();
                if (!isEmpty(moving)) {
                    int original = moving.getAmount();
                    int remaining = insertIntoGrid(top, moving);
                    if (remaining <= 0) {
                        event.getClickedInventory().setItem(event.getSlot(), null);
                    } else if (remaining < original) {
                        ItemStack left = moving.clone();
                        left.setAmount(remaining);
                        event.getClickedInventory().setItem(event.getSlot(), left);
                    }
                }
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

        if (isEditable(slot)) {
            ItemStack current = top.getItem(slot);
            boolean hasItem = current != null && !current.getType().isAir();
            boolean emptyHand = isEmpty(event.getCursor());
            if (hasItem && emptyHand && event.getClick() == ClickType.RIGHT) {
                event.setCancelled(true);
                if (slot == EditorSession.RESULT_SLOT) {
                    EditorDialogs.openResult(player, session);
                } else {
                    EditorDialogs.openIngredient(player, session, EditorSession.gridIndex(slot));
                }
            }
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

    private static int insertIntoGrid(Inventory top, ItemStack moving) {
        int remaining = moving.getAmount();
        for (int slot : EditorSession.GRID_SLOTS) {
            ItemStack existing = top.getItem(slot);
            if (isEmpty(existing) || !existing.isSimilar(moving)) {
                continue;
            }
            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space <= 0) {
                continue;
            }
            int moved = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + moved);
            top.setItem(slot, existing);
            remaining -= moved;
            if (remaining == 0) {
                return 0;
            }
        }
        for (int slot : EditorSession.GRID_SLOTS) {
            if (isEmpty(top.getItem(slot))) {
                ItemStack placed = moving.clone();
                placed.setAmount(remaining);
                top.setItem(slot, placed);
                return 0;
            }
        }
        return remaining;
    }

    private void reloadAfterSave(String file, String previousFile) {
        if (previousFile != null && !previousFile.equals(file)) {
            reloadOrUnload(previousFile);
        }
        reloadOrUnload(file);
    }

    private void reloadOrUnload(String relative) {
        try {
            plugin.getRecipeLoader().reloadFile(relative);
        } catch (NoSuchFileException e) {
            plugin.getRecipeLoader().unloadFile(relative);
        } catch (IOException e) {
            logger.log(Level.WARNING, "重载配方文件 " + relative + " 失败", e);
        }
    }

    private void save(Player player, EditorSession session) {
        Inventory editor = session.getInventory();
        if (editor == null) {
            return;
        }
        try {
            String previousFile = session.getSourceFile();
            String file = writer.save(session, editor);
            reloadAfterSave(file, previousFile);
            if (plugin.getRecipeLoader().getAllRecipeKeys().contains(session.getRecipeId())) {
                player.sendRichMessage("<green>已保存到 recipes/" + file);
                player.sendActionBar(Component.text("已保存 recipes/" + file, NamedTextColor.GREEN));
            } else {
                player.sendRichMessage("<red>已写入 recipes/" + file + "，但加载失败，详见控制台");
                player.sendActionBar(Component.text("配方加载失败，详情见控制台", NamedTextColor.RED));
            }
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
            logger.log(Level.WARNING, "保存配方时出错", e);
            player.sendActionBar(Component.text("保存失败，详情见控制台", NamedTextColor.RED));
        } catch (LinkageError e) {
            logger.log(Level.SEVERE, "保存配方时类加载失败", e);
            player.sendRichMessage("<red>保存失败：组件序列化类加载异常，详情见控制台");
            player.sendActionBar(Component.text("保存失败：类加载异常", NamedTextColor.RED));
        }
    }

    private void delete(Player player, EditorSession session) {
        if (!session.isEditingExisting()) {
            return;
        }
        String id = session.getSourceRecipeId();
        String sourceFile = session.getSourceFile();
        try {
            boolean removed = writer.delete(session);
            if (sourceFile != null) {
                reloadOrUnload(sourceFile);
            }
            if (removed) {
                player.sendRichMessage("<green>已删除配方 '" + id + "'");
            }
            session.setSourceRecipeId(null);
            session.setSourceFile(null);
            session.setSourceRecipeKey(null);
            player.closeInventory();
        } catch (IOException e) {
            player.sendRichMessage("<red>删除失败：" + e.getMessage());
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "删除配方时出错", e);
            player.sendRichMessage("<red>删除失败，详情见控制台");
        }
    }

    private static boolean isEditable(int slot) {
        return slot == EditorSession.RESULT_SLOT || EditorSession.gridIndex(slot) >= 0;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private static void returnItem(Player player, Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (isEmpty(item)) {
            return;
        }
        inventory.setItem(slot, null);
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
