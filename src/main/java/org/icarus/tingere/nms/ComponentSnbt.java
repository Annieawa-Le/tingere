package org.icarus.tingere.nms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.component.ComponentDialect;

/**
 * 数据组件的导出。
 */
public final class ComponentSnbt {

    private ComponentSnbt() {
    }

    public static String exportAll(ItemStack item) {
        Tag tag = DataComponentPatch.CODEC
                .encodeStart(registryOps(), handle(item).getComponentsPatch())
                .getOrThrow();
        return tag.toString();
    }

    public static ObjectNode exportJson(ItemStack item) {
        JsonNode encoded = ComponentNbt.encode(handle(item).getComponentsPatch());
        ObjectNode result = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        encoded.fields().forEachRemaining(entry ->
                result.set(ComponentDialect.canonicalKey(stripNamespace(entry.getKey())), entry.getValue()));
        return result;
    }

    private static String stripNamespace(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? key : key.substring(colon + 1);
    }

    /**
     * 组件编码必须跑在带注册表的 ops 上：附魔、属性这些是数据包注册表里的条目，
     * 裸 {@code NbtOps} 查不到它们，编码会直接抛 "Can't access registry"。
     */
    private static RegistryOps<Tag> registryOps() {
        return RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,
                MinecraftServer.getServer().registryAccess());
    }

    private static net.minecraft.world.item.ItemStack handle(ItemStack item) {
        return CraftItemStack.asNMSCopy(item);
    }
}

