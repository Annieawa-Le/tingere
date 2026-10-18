package org.icarus.tingere.nms;

import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public final class ItemRetyper {

    private ItemRetyper() {
    }

    public static ItemStack retype(Consumer<String> warn, ItemStack source, Material material) {
        net.minecraft.world.item.ItemStack from = CraftItemStack.asNMSCopy(source);
        net.minecraft.world.item.ItemStack to = CraftItemStack.asNMSCopy(new ItemStack(material, source.getAmount()));
        if (from == null || to == null) {
            warn.accept("Cannot retype " + source.getType() + " -> " + material
                    + " without NMS handles; its components will be lost.");
            return source.withType(material);
        }
        to.applyComponents(from.getComponentsPatch());
        return CraftItemStack.asBukkitCopy(to);
    }
}
