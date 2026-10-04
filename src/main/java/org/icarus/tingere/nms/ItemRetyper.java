package org.icarus.tingere.nms;

import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/**
 * 换材质但一个组件都不丢。
 * <p>
 * 为什么不直接用 {@link ItemStack#withType(Material)}：看 CraftItemStack#withType 的字节码，
 * 它在把源栈的组件补丁套到新栈上之后，还多做了一次
 * {@code result.setItemMeta(result.getItemMeta())} 的自我往返。ItemMeta 只表达得了组件体系的
 * 一部分，这一趟读出来再写回去会掉东西——附魔就掉在这里。实测：带附魔的铁剑经它一转，
 * 附魔全没了。
 * <p>
 * 这里只做两件事：造一个目标材质的新栈，把源栈的组件补丁原样套上去。没有中间表示，
 * 附魔、自定义名、lore、NBT 组件全都原样过去。
 */
public final class ItemRetyper {

    private ItemRetyper() {
    }

    /** 返回一个材质为 {@code material}、数据与 {@code source} 一致的新栈。 */
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
