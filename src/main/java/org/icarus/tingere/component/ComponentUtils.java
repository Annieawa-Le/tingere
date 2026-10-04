package org.icarus.tingere.component;

import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ComponentUtils {

    private ComponentUtils() {
    }

    // ------------------------------------------------------------------ key

    public static String toUnderscore(String value) {
        return value.replace('-', '_');
    }

    /**
     * 把任意写法规范化成组件的 local key。
     */
    public static String keyStandard(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace('/', '_');
        int colon = normalized.indexOf(':');
        return colon >= 0 ? normalized.substring(colon + 1) : normalized;
    }

    public static NamespacedKey parseKey(Plugin plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.contains(":")) {
            return NamespacedKey.fromString(value);
        }
        return plugin == null ? NamespacedKey.minecraft(value) : new NamespacedKey(plugin, value);
    }

    // --------------------------------------------------------------- JsonNode

    public static String text(JsonNode node, String field) {
        JsonNode value = find(node, field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public static boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode value = find(node, field);
        return value == null || value.isNull() ? fallback : value.asBoolean(fallback);
    }

    public static int intValue(JsonNode node, String field, int fallback) {
        JsonNode value = find(node, field);
        return value == null || value.isNull() ? fallback : value.asInt(fallback);
    }

    public static double doubleValue(JsonNode node, String field, double fallback) {
        JsonNode value = find(node, field);
        return value == null || value.isNull() ? fallback : value.asDouble(fallback);
    }

    /**
     * 找字段：先按给的写法找，找不到就试连字符 / 下划线互换。
     * <p>
     */
    public static JsonNode find(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode direct = node.get(field);
        if (direct != null) {
            return direct;
        }
        String swapped = field.indexOf('_') >= 0
                ? field.replace('_', '-')
                : field.replace('-', '_');
        return node.get(swapped);
    }

    /** 无论是单个字符串还是字符串列表，都收集成列表。 */
    public static List<String> strings(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node == null || node.isNull()) {
            return result;
        }
        if (node.isArray()) {
            for (JsonNode entry : node) {
                result.add(entry.asText());
            }
        } else {
            result.add(node.asText());
        }
        return result;
    }

    // --------------------------------------------------------------- reflect

    public static Class<?> toClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            return toClass(parameterized.getRawType());
        }
        if (type instanceof WildcardType wildcard && wildcard.getUpperBounds().length > 0) {
            return toClass(wildcard.getUpperBounds()[0]);
        }
        return null;
    }
}

