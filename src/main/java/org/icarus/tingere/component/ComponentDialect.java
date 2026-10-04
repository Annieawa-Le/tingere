package org.icarus.tingere.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 配置方言 → 原版组件结构。
 * 为了兼容性说是
 */
@UtilityClass
public class ComponentDialect {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    // 这里是键的别名。。
    private static final Map<String, String> EXPLICIT_ALIASES = Map.ofEntries(
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
            // 这些组件的注册表 key 里带斜杠，配置里按字段名写法补一道
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

    private static final Set<String> TEXT_KEYS = Set.of(
            "custom_name", "item_name", "lore");


    private static final Map<String, java.util.function.UnaryOperator<JsonNode>> SWEETENERS = Map.of(
            "tooltip_display", value -> {
                if (!value.isBoolean()) {
                    return value;
                }
                // 组件里存的是"要隐藏什么"，配置里写 true 想表达的正好是"不隐藏"
                return JsonNodeFactory.instance.objectNode().put("hide_tooltip", !value.asBoolean());
            });


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
            } else if (SWEETENERS.containsKey(key)) {
                value = SWEETENERS.get(key).apply(value);
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

    public static String namespaced(String raw, String fallbackNamespace) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.contains(":")) {
            return value.toLowerCase(Locale.ROOT);
        }
        return (fallbackNamespace == null ? "minecraft" : fallbackNamespace) + ':' + value.toLowerCase(Locale.ROOT);
    }


    public static JsonNode textNode(JsonNode value) {
        if (value == null || value.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (value.isObject() || value.isArray()) {
            return value;
        }
        return JsonNodeFactory.instance.textNode(text(value.asText()));
    }

   // MiniMessage → JSON 
    private static String text(String raw) {
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

    public static String firstText(JsonNode node, String... fields) {
        JsonNode value = first(node, fields);
        return value == null ? null : value.asText();
    }

    public static int intValue(JsonNode node, int fallback, String... fields) {
        JsonNode value = first(node, fields);
        return value == null || !value.isNumber() ? fallback : value.asInt(fallback);
    }

    public static List<JsonNode> array(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            node.forEach(result::add);
            return result;
        }
        return List.of(node);
    }

    public static UUID uuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
