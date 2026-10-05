package org.icarus.tingere.gui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** 把编辑会话挂到界面上，事件里靠 {@code getHolder()} 认出来。 */
@RequiredArgsConstructor
public class EditorHolder implements InventoryHolder {

    @Getter
    private final EditorSession session;

    private Inventory inventory;

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
