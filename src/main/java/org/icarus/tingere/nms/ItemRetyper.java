package org.icarus.tingere.nms;

import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/**
 * 这段代码专门用来做材质转换，
 * 返回转换后组件相同的不同材质物品。
 */
public final class ItemRetyper {

    private ItemRetyper() {
    }

    public static ItemStack retype(ItemStack source, Material material) {
        net.minecraft.world.item.ItemStack from = CraftItemStack.asNMSCopy(source);
        net.minecraft.world.item.ItemStack to = CraftItemStack.asNMSCopy(new ItemStack(material, source.getAmount()));
        if (from == null || to == null) {
            return source.withType(material);
        }
        to.applyComponents(from.getComponentsPatch());
        return CraftItemStack.asBukkitCopy(to);
    }
}
