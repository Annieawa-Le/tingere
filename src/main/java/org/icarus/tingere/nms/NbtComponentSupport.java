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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;

import org.icarus.tingere.parser.ParseProblem;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NbtComponentSupport {

    private static final Pattern NUMBER_WITH_SUFFIX = Pattern.compile("^(-?\\d+)([bslf])$", Pattern.CASE_INSENSITIVE);

    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg";

    private NbtComponentSupport() {
    }

    /**
     * 我怎么不记得要用这个...
     */
    public static void applyEntityData(ItemStack item, JsonNode data) {
        if (item == null ||
                item.isEmpty() ||
                data == null ||
                !data.isObject() ||
                data.isEmpty()) {
            return;
        }

        JsonNode typeNode = data.get("type");
        JsonNode payload = data;
        EntityType<?> type;
        if (typeNode != null && !typeNode.isNull()) {
            type = lookupEntityType(typeNode.asText());
            if (type == null) {
                throw new ParseProblem.Component("entity_data",
                        "unknown entity type '" + typeNode.asText() + "'");
            }
            ObjectNode stripped = ((ObjectNode) data).deepCopy();
            stripped.remove("type");
            payload = stripped;
        } else {
            type = inferEntityType(item);
            if (type == null) {
                throw new ParseProblem.Component("entity_data",
                        "cannot infer the entity type of " + item.getItem()
                                + "; set an explicit 'type' field to use entity-data");
            }
        }

        item.set(DataComponents.ENTITY_DATA, TypedEntityData.of(type, compound(payload)));
    }


    private static EntityType<?> inferEntityType(ItemStack item) {
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
        if (key == null) {
            return null;
        }
        // 必须用 getOptional：ENTITY_TYPE 是 DefaultedRegistry，它的 getValue 对未知键
        // 会回退到默认值（minecraft:pig）而不是 null —— 用 getValue 的话拼错实体名
        // 会静默变成一只猪
        // ！？猪？！
        return BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
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


