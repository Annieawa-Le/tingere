package org.icarus.tingere.nms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.TypedEntityData;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.entity.CraftEntityType;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code entity-data} 组件的写入通道。
 * <p>
 * {@code minecraft:entity_data} 是 NBT 型组件，paper-api 的 {@code DataComponentTypes}
 * 里没有它的条目——那不是漏扫，是上游确实没暴露（96 个字段里没有）。
 * 想写它就只能下到 NMS 这一层，这也是本类待在 {@code nms} 包而不是 {@code component} 包的原因。
 * <p>
 * 隐形物品展示框靠的就是它：原版把 {@code Invisible} 记在实体的 NBT 上，
 * 物品展示框物品通过 {@code entity_data} 把这段 NBT 带进放置后的实体。
 * <pre>
 * entity-data:
 *   Invisible: true          # 实体类型由物品材质推断：ITEM_FRAME → item_frame
 * </pre>
 * <pre>
 * entity-data:
 *   type: item_frame         # 也可以显式指定（推断不出来的材质必须写）
 *   data:
 *     Invisible: true
 * </pre>
 * 值到 NBT 的映射：{@code true/false} → {@code 1b/0b}（NBT 没有布尔，惯例写 byte），
 * 整数 → {@code IntTag}，小数 → {@code DoubleTag}，字符串默认 {@code StringTag}；
 * 要精确类型就带后缀：{@code 3b} / {@code 3s} / {@code 3L} / {@code 3f}。
 * 嵌套映射与列表会递归成 {@code CompoundTag} / {@code ListTag}。
 */
public final class NbtComponentSupport {

    private static final Pattern NUMBER_WITH_SUFFIX = Pattern.compile("^(-?\\d+)([bslf])$", Pattern.CASE_INSENSITIVE);

    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg";

    private NbtComponentSupport() {
    }

    /**
     * 把 {@code entity_data} 写进物品。
     * <p>
     * 走的是"复制进 NMS、改完再复制回来"的往返，所以返回的是新对象而不是就地修改，
     * 调用方必须接住返回值。
     */
    public static ItemStack applyEntityData(ItemStack item, JsonNode data, Logger logger) {
        if (item == null || item.getType().isAir() || data == null || !data.isObject() || data.isEmpty()) {
            return item;
        }

        JsonNode typeNode = data.get("type");
        JsonNode payload = data;
        EntityType<?> type;
        if (typeNode != null && !typeNode.isNull()) {
            type = lookupEntityType(typeNode.asText());
            if (type == null) {
                logger.warning("Unknown entity-data type '" + typeNode.asText() + "', entity-data skipped.");
                return item;
            }
            ObjectNode stripped = ((ObjectNode) data).deepCopy();
            stripped.remove("type");
            payload = stripped;
        } else {
            type = inferEntityType(item.getType());
            if (type == null) {
                logger.warning("Cannot infer the entity type of " + item.getType()
                        + " for entity-data; set an explicit 'type' field to use it.");
                return item;
            }
        }

        net.minecraft.world.item.ItemStack handle = CraftItemStack.asNMSCopy(item);
        if (handle == null) {
            return item;
        }
        handle.set(DataComponents.ENTITY_DATA, TypedEntityData.of(type, compound(payload)));
        return CraftItemStack.asBukkitCopy(handle);
    }

    // ------------------------------------------------------------ 实体类型

    /** 按物品材质猜实体类型：{@code ITEM_FRAME} → {@code item_frame}，刷怪蛋剥掉后缀。 */
    private static EntityType<?> inferEntityType(Material material) {
        String name = material.getKey().getKey();
        EntityType<?> direct = lookupEntityType(name);
        if (direct != null) {
            return direct;
        }
        return name.endsWith(SPAWN_EGG_SUFFIX)
                ? lookupEntityType(name.substring(0, name.length() - SPAWN_EGG_SUFFIX.length()))
                : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EntityType<?> lookupEntityType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = value.contains(":")
                ? NamespacedKey.fromString(value)
                : NamespacedKey.minecraft(value);
        if (key == null) {
            return null;
        }
        Registry registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENTITY_TYPE);
        if (registry == null) {
            return null;
        }
        // Bukkit 侧查到的是 org.bukkit.entity.EntityType，TypedEntityData 要的是 NMS 的，必须转一层
        Object entry = registry.get(key);
        return entry instanceof org.bukkit.entity.EntityType bukkit ? CraftEntityType.bukkitToMinecraft(bukkit) : null;
    }

    // ------------------------------------------------------------------ NBT

    private static CompoundTag compound(JsonNode node) {
        CompoundTag tag = new CompoundTag();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            Tag value = tagOf(entry.getValue());
            if (value != null) {
                tag.put(entry.getKey(), value);
            }
        }
        return tag;
    }

    private static Tag tagOf(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            return compound(node);
        }
        if (node.isArray()) {
            ListTag list = new ListTag();
            for (JsonNode entry : node) {
                Tag value = tagOf(entry);
                if (value != null) {
                    list.add(value);
                }
            }
            return list;
        }
        if (node.isBoolean()) {
            return ByteTag.valueOf(node.asBoolean());
        }
        if (node.isIntegralNumber()) {
            return IntTag.valueOf(node.asInt());
        }
        if (node.isNumber()) {
            return DoubleTag.valueOf(node.asDouble());
        }
        return stringTag(node.asText());
    }

    private static Tag stringTag(String raw) {
        Matcher matcher = NUMBER_WITH_SUFFIX.matcher(raw.trim());
        if (!matcher.matches()) {
            return StringTag.valueOf(raw);
        }
        long value = Long.parseLong(matcher.group(1));
        return switch (Character.toLowerCase(matcher.group(2).charAt(0))) {
            case 'b' -> ByteTag.valueOf((byte) value);
            case 's' -> ShortTag.valueOf((short) value);
            case 'l' -> LongTag.valueOf(value);
            default -> FloatTag.valueOf(value);
        };
    }
}

