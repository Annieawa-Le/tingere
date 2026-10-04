package org.icarus.tingere.nms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code entity-data} 组件的写入通道。
 * 没有对应的组件API。
 */
public final class NbtComponentSupport {

    private static final Pattern NUMBER_WITH_SUFFIX = Pattern.compile("^(-?\\d+)([bslf])$", Pattern.CASE_INSENSITIVE);

    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg";

    private NbtComponentSupport() {
    }

    public static net.minecraft.world.item.ItemStack applyEntityData(net.minecraft.world.item.ItemStack item,
                                                                     JsonNode data, Logger logger) {
        if (item == null || item.isEmpty() || data == null || !data.isObject() || data.isEmpty()) {
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
            type = inferEntityType(item);
            if (type == null) {
                logger.warning("Cannot infer the entity type of " + item.getItem()
                        + " for entity-data; set an explicit 'type' field to use it.");
                return item;
            }
        }

        item.set(DataComponents.ENTITY_DATA, TypedEntityData.of(type, compound(payload)));
        return item;
    }


    private static EntityType<?> inferEntityType(net.minecraft.world.item.ItemStack item) {
        String name = BuiltInRegistries.ITEM.getKey(item.getItem()).getPath();
        EntityType<?> direct = lookupEntityType(name);
        if (direct != null) {
            return direct;
        }
        return name.endsWith(SPAWN_EGG_SUFFIX)
                ? lookupEntityType(name.substring(0, name.length() - SPAWN_EGG_SUFFIX.length()))
                : null;
    }

    private static EntityType<?> lookupEntityType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        Identifier key = value.contains(":")
                ? Identifier.tryParse(value)
                : Identifier.withDefaultNamespace(value);
        return key == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(key);
    }


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


