package org.icarus.tingere.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DSL 中其它类型写法到原版映射
 * 为了兼容性说是
 */
@UtilityClass
public class ComponentDialect {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
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
            Map.entry("equippable_on_head", "equippable"));

    private static final Set<String> TEXT_KEYS = Set.of("custom_name", "item_name", "lore");

    private static final Set<String> EXTERNAL_KEYS = Set.of("entity_data");


    public static ObjectNode toVanilla(JsonNode components) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        if (components == null || !components.isObject()) {
            return result;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = components.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = canonicalKey(entry.getKey());
            if (key == null) {
                continue;
            }
            if (EXTERNAL_KEYS.contains(key)) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (TEXT_KEYS.contains(key)) {
                value = textNode(value);
            }
            result.set(key, value);
        }
        return result;
    }


    public static String canonicalKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace('/', '_');
        int colon = normalized.indexOf(':');
        if (colon >= 0) {
            normalized = normalized.substring(colon + 1);
        }
        String alias = EXPLICIT_ALIASES.get(normalized);
        return alias == null ? normalized : alias;
    }

    public static JsonNode textNode(JsonNode value) {
        if (value == null || value.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (value.isObject() || value.isArray()) {
            return value;
        }
        return JsonNodeFactory.instance.textNode(toMinimessageString(value.asText()));
    }

    private static String toMinimessageString(String raw) {
        return GsonComponentSerializer.gson().serialize(MINI_MESSAGE.deserialize(raw == null ? "" : raw));
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
