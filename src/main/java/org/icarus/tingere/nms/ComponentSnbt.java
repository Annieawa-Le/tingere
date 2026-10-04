package org.icarus.tingere.nms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.component.ComponentUtils;

/**
 * 数据组件的导出。
 * <p>
 * 为什么绕这一趟 NMS：Bukkit 侧的 {@code ItemStack} 拿不到"与默认值不同的组件"的可序列化视图，
 * 而配方落盘要的正是这一份差量。{@link DataComponentPatch#CODEC} 是唯一能一把拿全的入口，
 * 省得给几百个组件各写一份序列化。
 * <p>
 * 组件屏已经撤掉了，所以这里只出不进——物品的组件由玩家自己做好再放进格子。
 */
public final class ComponentSnbt {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ComponentSnbt() {
    }

    /** 物品上"与默认不同"的组件，全量导出成 SNBT。 */
    public static String exportAll(ItemStack item) {
        Tag tag = DataComponentPatch.CODEC
                .encodeStart(registryOps(NbtOps.INSTANCE), handle(item).getComponentsPatch())
                .getOrThrow();
        return tag.toString();
    }

    /**
     * 物品的组件 → 项目配方 YAML 里的 {@code components} 节点。
     * <p>
     * key 转成项目自己的写法（去命名空间 + kebab），值保持原版结构——这样
     * {@code ComponentRegistry} 重新加载这份 YAML 时认得出，不用另写一套解析。
     */
    public static JsonNode exportJson(ItemStack item) {
        JsonElement encoded = DataComponentPatch.CODEC
                .encodeStart(registryOps(JsonOps.INSTANCE), handle(item).getComponentsPatch())
                .getOrThrow();
        ObjectNode result = MAPPER.createObjectNode();
        if (!encoded.isJsonObject()) {
            return result;
        }
        for (java.util.Map.Entry<String, JsonElement> entry : encoded.getAsJsonObject().entrySet()) {
            result.set(ComponentUtils.keyStandard(stripNamespace(entry.getKey())), toNode(entry));
        }
        return result;
    }

    /**
     * Gson 的 {@code JsonElement} 不能直接喂给 Jackson 的 {@code valueToTree}——那边的树只认
     * 自己，遇到 Gson 对象会退化成反射它的 getter（一律从 {@code getAsDouble()} 开始炸）。
     * 走一趟标准 JSON 文本最省事，两边都认这个格式。
     */
    private static JsonNode toNode(java.util.Map.Entry<String, JsonElement> entry) {
        try {
            return MAPPER.readTree(entry.getValue().toString());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法导出组件 " + entry.getKey() + "：" + e.getMessage(), e);
        }
    }

    private static String stripNamespace(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? key : key.substring(colon + 1);
    }

    /**
     * 组件编码必须跑在带注册表的 ops 上：附魔、属性这些是数据包注册表里的条目，
     * 裸 {@code JsonOps} / {@code NbtOps} 查不到它们，编码会直接抛 "Can't access registry"。
     */
    private static <T> RegistryOps<T> registryOps(DynamicOps<T> ops) {
        return RegistryOps.create(ops, MinecraftServer.getServer().registryAccess());
    }

    private static net.minecraft.world.item.ItemStack handle(ItemStack item) {
        return CraftItemStack.asNMSCopy(item);
    }
}

