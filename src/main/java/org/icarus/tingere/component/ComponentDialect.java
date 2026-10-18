package org.icarus.tingere.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * DSL 到原版组件映射
 * 由于你的溜溜梅统一性这一块做的不是很好导致我原来的写法全部被操。！飞了
 */
@UtilityClass
public class ComponentDialect {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private static final List<String> LEGACY_ATTRIBUTE_PREFIXES = List.of("generic.", "player.", "zombie.", "horse.");

    private static final Set<String> EQUIPMENT_SLOTS = Set.of(
            "head", "chest", "legs", "feet", "body", "mainhand", "offhand");

    private static final Set<String> SLOT_GROUPS = Set.of(
            "any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body", "saddle");

    private static final Map<String, String> OPERATION_ALIASES = Map.ofEntries(
            Map.entry("add_value", "add_value"),
            Map.entry("add_number", "add_value"),
            Map.entry("add_multiplied_base", "add_multiplied_base"),
            Map.entry("add_scalar", "add_multiplied_base"),
            Map.entry("add_multiplied_total", "add_multiplied_total"),
            Map.entry("multiply_scalar_1", "add_multiplied_total"),
            Map.entry("multiply_scalar", "add_multiplied_total"));

    private static final Map<String, String> EXPLICIT_ALIASES = Map.<String, String>ofEntries(
            Map.entry("display_name", "custom_name"),
            Map.entry("name", "custom_name"),
            Map.entry("maxdamage", "max_damage"),
            Map.entry("max_stack", "max_stack_size"),
            Map.entry("stack_size", "max_stack_size"),
            Map.entry("attributes", "attribute_modifiers"),
            Map.entry("attribute", "attribute_modifiers"),
            Map.entry("enchant", "enchantments"),
            Map.entry("enchantment", "enchantments"),
            Map.entry("stored_enchant", "stored_enchantments"),
            Map.entry("glint", "enchantment_glint_override"),
            Map.entry("model", "item_model"),
            Map.entry("model_data", "custom_model_data"),
            Map.entry("tooltip", "tooltip_display"),
            Map.entry("hide_tooltip", "tooltip_display"),
            Map.entry("color", "dyed_color"),
            Map.entry("dye", "dyed_color"),
            Map.entry("armor_trim", "trim"),
            Map.entry("damage_resistance", "damage_resistant"),
            Map.entry("stew_effects", "suspicious_stew_effects"),
            Map.entry("recipe_unlock", "recipes"),
            Map.entry("written_book", "written_book_content"),
            Map.entry("writable_book", "writable_book_content"),
            Map.entry("equippable_on_head", "equippable"),
            // 动物组件为刷怪蛋所用
            Map.entry("villager_variant", "villager/variant"),
            Map.entry("wolf_variant", "wolf/variant"),
            Map.entry("wolf_sound_variant", "wolf/sound_variant"),
            Map.entry("cat_variant", "cat/variant"),
            Map.entry("cat_sound_variant", "cat/sound_variant"),
            Map.entry("chicken_variant", "chicken/variant"),
            Map.entry("chicken_sound_variant", "chicken/sound_variant"),
            Map.entry("cow_variant", "cow/variant"),
            Map.entry("cow_sound_variant", "cow/sound_variant"),
            Map.entry("pig_variant", "pig/variant"),
            Map.entry("pig_sound_variant", "pig/sound_variant"),
            Map.entry("frog_variant", "frog/variant"),
            Map.entry("axolotl_variant", "axolotl/variant"),
            Map.entry("rabbit_variant", "rabbit/variant"),
            Map.entry("horse_variant", "horse/variant"),
            Map.entry("llama_variant", "llama/variant"),
            Map.entry("fox_variant", "fox/variant"),
            Map.entry("salmon_size", "salmon/size"),
            Map.entry("painting_variant", "painting/variant"),
            Map.entry("mooshroom_variant", "mooshroom/variant"),
            Map.entry("sheep_color", "sheep/color"),
            Map.entry("shulker_color", "shulker/color"),
            Map.entry("tropical_fish_base_color", "tropical_fish/base_color"),
            Map.entry("tropical_fish_pattern", "tropical_fish/pattern"),
            Map.entry("tropical_fish_pattern_color", "tropical_fish/pattern_color"),
            Map.entry("parrot_variant", "parrot/variant"),
            Map.entry("zombie_nautilus_variant", "zombie_nautilus/variant"),
            Map.entry("wolf_collar", "wolf/collar"),
            Map.entry("cat_collar", "cat/collar"));

    private static final Set<String> TEXT_KEYS = Set.of("custom_name", "item_name");

    private static final Set<String> EXTERNAL_KEYS = Set.of("entity_data");

    public static ObjectNode toVanilla(JsonNode components, String namespace, Consumer<String> warning) {
        ObjectNode result = NODES.objectNode();
        if (components == null || !components.isObject()) {
            return result;
        }
        String fallbackNamespace = namespace == null || namespace.isBlank()
                ? "minecraft"
                : namespace.trim().toLowerCase(Locale.ROOT);

        Iterator<Map.Entry<String, JsonNode>> fields = components.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = canonicalKey(entry.getKey());
            if (key == null || EXTERNAL_KEYS.contains(key)) {
                continue;
            }
            JsonNode value = normalize(key, entry.getValue(), fallbackNamespace, warning);
            if (value != null) {
                result.set(key, value);
            }
        }
        return result;
    }

    private static JsonNode normalize(String key, JsonNode value, String namespace, Consumer<String> warning) {
        if (value == null || value.isNull()) {
            return null;
        }
        return switch (key) {
            case "lore" -> lore(value);
            case "custom_model_data" -> customModelData(value, warning);
            case "enchantments", "stored_enchantments" -> enchantments(key, value, warning);
            case "attribute_modifiers" -> attributes(value, namespace, warning);
            case "equippable" -> equippable(value, warning);
            case "item_model" -> itemModel(value, namespace);
            case "tooltip_display" -> tooltipDisplay(value);
            case "unbreakable" -> unbreakable(value);
            default -> TEXT_KEYS.contains(key) ? text(value) : value;
        };
    }

    private static JsonNode lore(JsonNode value) {
        ArrayNode lines = NODES.arrayNode();
        if (value.isArray()) {
            for (JsonNode line : value) {
                addLine(lines, line);
            }
        } else {
            addLine(lines, value);
        }
        return lines;
    }

    private static void addLine(ArrayNode lines, JsonNode line) {
        JsonNode converted = text(line);
        if (converted != null) {
            lines.add(converted);
        }
    }

    private static JsonNode text(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isObject() || value.isArray()) {
            return value;
        }
        String raw = value.asText();
        JsonNode embedded = embeddedJson(raw);
        if (embedded != null) {
            return embedded;
        }
        try {
            String json = GsonComponentSerializer.gson().serialize(
                    MINI_MESSAGE.deserialize(raw == null ? "" : raw));
            JsonNode structured = embeddedJson(json);
            return structured == null ? NODES.textNode(raw) : structured;
        } catch (RuntimeException e) {
            return NODES.textNode(raw);
        }
    }

    private static JsonNode embeddedJson(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(trimmed);
            return node != null && (node.isObject() || node.isArray()) ? node : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static JsonNode customModelData(JsonNode value, Consumer<String> warning) {
        ObjectNode result = NODES.objectNode();
        if (value.isNumber()) {
            result.putArray("floats").add(value.asDouble());
            return result;
        }
        if (value.isArray()) {
            ArrayNode floats = result.putArray("floats");
            ArrayNode flags = null;
            ArrayNode strings = null;
            for (JsonNode entry : value) {
                if (entry.isNumber()) {
                    floats.add(entry.asDouble());
                } else if (entry.isBoolean()) {
                    if (flags == null) {
                        flags = result.putArray("flags");
                    }
                    flags.add(entry.asBoolean());
                } else if (entry.isTextual()) {
                    if (strings == null) {
                        strings = result.putArray("strings");
                    }
                    strings.add(entry.asText());
                }
            }
            if (floats.isEmpty()) {
                result.remove("floats");
            }
            return result;
        }
        if (value.isObject()) {
            return value;
        }
        warning.accept("ignored custom-model-data: expected an integer, got " + value.getNodeType());
        return null;
    }

    private static JsonNode enchantments(String key, JsonNode value, Consumer<String> warning) {
        if (value.isArray()) {
            ObjectNode levels = NODES.objectNode();
            for (JsonNode entry : value) {
                addEnchantment(levels, entry.asText(), warning);
            }
            return levels;
        }
        if (!value.isObject()) {
            warning.accept("ignored " + key + ": expected a list like 'minecraft:sharpness:3'");
            return null;
        }
        if (value.hasNonNull("levels") && value.get("levels").isObject()) {
            return value.get("levels");
        }
        ObjectNode levels = NODES.objectNode();
        value.fields().forEachRemaining(entry -> {
            JsonNode level = entry.getValue();
            levels.set(entry.getKey(), level.isNumber() ? level : NODES.numberNode(level.asInt(1)));
        });
        return levels;
    }

    private static void addEnchantment(ObjectNode levels, String raw, Consumer<String> warning) {
        String entry = raw == null ? "" : raw.trim();
        int separator = entry.lastIndexOf(':');
        String id = separator <= 0 ? entry : entry.substring(0, separator).trim();
        String level = separator <= 0 ? "1" : entry.substring(separator + 1).trim();
        if (id.isBlank()) {
            warning.accept("ignored enchantment entry '" + raw + "': missing enchantment id");
            return;
        }
        try {
            levels.put(id, Integer.parseInt(level));
        } catch (NumberFormatException e) {
            warning.accept("ignored enchantment entry '" + raw + "': illegal level");
        }
    }

    private static JsonNode attributes(JsonNode value, String namespace, Consumer<String> warning) {
        JsonNode list = value;
        if (value.isObject()) {
            list = value.get("modifiers");
            if (list == null || !list.isArray()) {
                return value;
            }
        }
        if (!list.isArray()) {
            warning.accept("ignored attributes: expected a list of modifiers");
            return null;
        }

        ArrayNode modifiers = NODES.arrayNode();
        int index = 0;
        for (JsonNode entry : list) {
            index++;
            if (!entry.isObject()) {
                warning.accept("ignored attribute modifier #" + index + ": expected a mapping");
                continue;
            }
            ObjectNode modifier = attributeModifier(entry, namespace, index, warning);
            if (modifier != null) {
                modifiers.add(modifier);
            }
        }
        return modifiers;
    }

    private static ObjectNode attributeModifier(JsonNode entry, String namespace, int index,
                                                Consumer<String> warning) {
        String type = entry.hasNonNull("type") ? entry.get("type").asText() : null;
        if (type == null || type.isBlank()) {
            warning.accept("ignored attribute modifier #" + index + ": missing 'type'");
            return null;
        }
        JsonNode amount = entry.get("amount");
        if (amount == null || !amount.isNumber()) {
            warning.accept("ignored attribute modifier #" + index + " (" + type + "): 'amount' must be a number");
            return null;
        }
        String operation = resolveOperation(entry.hasNonNull("operation")
                ? entry.get("operation").asText()
                : null);
        if (operation == null) {
            warning.accept("ignored attribute modifier #" + index + " (" + type + "): unknown operation '"
                    + entry.get("operation").asText() + "'");
            return null;
        }

        ObjectNode modifier = NODES.objectNode();
        modifier.put("type", attributeId(type));
        modifier.put("id", entry.hasNonNull("id")
                ? entry.get("id").asText()
                : namespace + ":modifier_" + index);
        modifier.set("amount", amount);
        modifier.put("operation", operation);
        String slot = entry.hasNonNull("slot") ? entry.get("slot").asText().trim().toLowerCase(Locale.ROOT) : null;
        if (slot != null && SLOT_GROUPS.contains(slot) && !"any".equals(slot)) {
            modifier.put("slot", slot);
        }
        if (entry.hasNonNull("display")) {
            modifier.set("display", entry.get("display"));
        }
        return modifier;
    }

    private static String attributeId(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        String namespace = colon < 0 ? null : value.substring(0, colon);
        String path = colon < 0 ? value : value.substring(colon + 1);
        for (String legacy : LEGACY_ATTRIBUTE_PREFIXES) {
            if (path.startsWith(legacy)) {
                path = path.substring(legacy.length());
                break;
            }
        }
        return namespace == null ? path : namespace + ':' + path;
    }

    private static String resolveOperation(String raw) {
        if (raw == null || raw.isBlank()) {
            return "add_value";
        }
        return OPERATION_ALIASES.get(raw.trim().toLowerCase(Locale.ROOT));
    }

    private static JsonNode equippable(JsonNode value, Consumer<String> warning) {
        if (value.isBoolean()) {
            return value.asBoolean() ? NODES.objectNode().put("slot", "head") : null;
        }
        if (value.isTextual()) {
            String slot = value.asText().trim().toLowerCase(Locale.ROOT);
            if (!EQUIPMENT_SLOTS.contains(slot)) {
                warning.accept("ignored equippable slot '" + value.asText() + "'");
                return null;
            }
            return NODES.objectNode().put("slot", slot);
        }
        if (!value.isObject()) {
            warning.accept("ignored equippable: expected a boolean, slot name or mapping");
            return null;
        }
        ObjectNode copy = value.deepCopy();
        String slot = copy.hasNonNull("slot") ? copy.get("slot").asText().trim().toLowerCase(Locale.ROOT) : "head";
        if (!EQUIPMENT_SLOTS.contains(slot)) {
            warning.accept("ignored equippable slot '" + copy.get("slot").asText() + "'");
            return null;
        }
        copy.put("slot", slot);
        return copy;
    }

    private static JsonNode itemModel(JsonNode value, String namespace) {
        if (!value.isTextual()) {
            return value;
        }
        String raw = value.asText().trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank()) {
            return null;
        }
        return NODES.textNode(raw.contains(":") ? raw : namespace + ':' + raw);
    }

    private static JsonNode tooltipDisplay(JsonNode value) {
        if (!value.isBoolean()) {
            return value;
        }
        // 依旧小巧思
        return NODES.objectNode().put("hide_tooltip", !value.asBoolean());
    }

    private static JsonNode unbreakable(JsonNode value) {
        if (!value.isBoolean()) {
            return value;
        }
        return value.asBoolean() ? NODES.objectNode() : null;
    }

    public static String canonicalKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        boolean removed = value.startsWith("!");
        if (removed) {
            value = value.substring(1);
        }
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        String alias = EXPLICIT_ALIASES.get(value.replace('-', '_').replace('/', '_'));
        if (alias == null) {
            alias = value.replace('-', '_');
        }
        return removed ? '!' + alias : alias;
    }

    public static JsonNode first(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }
}
