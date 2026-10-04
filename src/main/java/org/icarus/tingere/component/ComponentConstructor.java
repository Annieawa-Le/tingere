package org.icarus.tingere.component;

import com.fasterxml.jackson.databind.JsonNode;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Locale;

/**
 * 把配置里的标量值构造成某个组件值类型。
 * <p>
 * 只用来自动注册那些"值本身就是一个数字 / 布尔 / 字符串 / 枚举 / Key / 文本组件"的组件。
 * 复合组件（Lore、Enchantments、Attributes 等）不走这里，而是在 {@link ComponentRegistry}
 * 里用 {@link ComponentBuilder} 显式构造后注册。
 * <p>
 * 注意这只是"自动兜底"层：它决定了哪些长尾组件无需手写就能直接用，
 * 而不是这套系统的主力。
 */
public final class ComponentConstructor {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private ComponentConstructor() {
    }

    /** 该类型能否由 {@link #construct} 自动构造。 */
    public static boolean supports(Class<?> type) {
        if (type == null) {
            return false;
        }
        return type == Integer.class
                || type == Long.class
                || type == Short.class
                || type == Byte.class
                || type == Float.class
                || type == Double.class
                || type == Boolean.class
                || type == String.class
                || Component.class.isAssignableFrom(type)
                || Key.class.isAssignableFrom(type)
                || type.isEnum();
    }

    /**
     * 按目标类型把 JsonNode 构造成对应的值对象。
     *
     * @return 构造结果；类型不支持或值非法时返回 {@code null}
     */
    public static Object construct(JsonNode value, Class<?> type, ComponentContext context) {
        if (value == null || value.isNull() || type == null) {
            return null;
        }
        if (type == Integer.class) {
            return value.asInt();
        }
        if (type == Long.class) {
            return value.asLong();
        }
        if (type == Short.class) {
            return (short) value.asInt();
        }
        if (type == Byte.class) {
            return (byte) value.asInt();
        }
        if (type == Float.class) {
            return (float) value.asDouble();
        }
        if (type == Double.class) {
            return value.asDouble();
        }
        if (type == Boolean.class) {
            return value.asBoolean();
        }
        if (type == String.class) {
            return value.asText();
        }
        if (Component.class.isAssignableFrom(type)) {
            return MINI_MESSAGE.deserialize(value.asText());
        }
        if (Key.class.isAssignableFrom(type)) {
            String raw = value.asText();
            return raw.contains(":") ? Key.key(raw) : Key.key(Key.MINECRAFT_NAMESPACE, raw);
        }
        if (type.isEnum()) {
            return enumValue(type, value.asText());
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf((Class) type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

