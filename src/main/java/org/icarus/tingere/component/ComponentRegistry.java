package org.icarus.tingere.component;

import com.fasterxml.jackson.databind.JsonNode;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.Tingere;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 数据组件注册表。
 * <p>
 * 这里维护两张表：
 * <ul>
 *   <li><b>显式层</b>：{@link #register} 注册的类型安全解码器，覆盖常用组件与所有需要
 *       builder 才能构造的复合组件；</li>
 *   <li><b>自动层</b>：{@link #registerAutomatic()} 反射枚举 {@link DataComponentTypes}
 *       的全部字段，凡是"值就是一个数字 / 布尔 / 字符串 / 枚举 / Key / 文本组件"的组件
 *       都会自动获得一个解码器，用来兜住长尾组件，省去逐一手写。</li>
 * </ul>
 * 两层共用一张 {@code appliers} 表，自动层只在 key 尚未被显式注册时才补位，
 * 因此显式层的语义永远优先。未命中任何一层的组件会记录一条警告并跳过，不影响其余组件。
 */
@SuppressWarnings("UnstableApiUsage")
public final class ComponentRegistry {

    private static final String[] LEGACY_ATTRIBUTE_PREFIXES = {"generic.", "player.", "zombie.", "horse."};
    private static final Set<String> NAME_KEYS = Set.of("display_name", "custom_name", "name", "prefix", "suffix");
    /** 由 NMS 通道写入的组件：paper-api 的组件表里没有它们，注册表只负责放过、不报未知。 */
    private static final Set<String> EXTERNAL_KEYS = Set.of("entity_data");

    @FunctionalInterface
    private interface Applier {
        void apply(ItemStack item, JsonNode value, ComponentContext context);
    }

    private final Map<String, Applier> appliers = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();
    private final Logger logger;

    public ComponentRegistry(Tingere plugin) {
        this.logger = plugin.getLogger();
        registerDefaults();
        ComponentComposites.registerAll(this);
        int automatic = registerAutomatic();
        logger.info("Component registry ready: " + appliers.size() + " component(s) available ("
                + automatic + " auto-registered).");
    }

    // ---------------------------------------------------------------- 注册

    /**
     * 类型安全的注册入口。泛型 {@code T} 在这一行由编译器固定，
     * 闭包把它带进表里，于是运行时既不需要反射也不用强制转换。
     */
    public <T> void register(DataComponentType.Valued<T> type, ComponentCodec<T> codec) {
        appliers.put(keyOf(type), (item, value, context) -> {
            T decoded = codec.decode(value, item, context);
            if (decoded != null) {
                item.setData(type, decoded);
            }
        });
    }

    /** 注册"存在即生效"的无值组件（如 unbreakable）。值解析为 false 时保持不设置。 */
    public void registerNonValued(DataComponentType.NonValued type) {
        appliers.put(keyOf(type), (item, value, context) -> {
            if (value == null || value.isNull() || value.isObject() || value.asBoolean(false)) {
                item.setData(type);
            }
        });
    }

    /** 为某个组件登记别名（例如 {@code display-name} → {@code custom_name}）。 */
    public void alias(String alias, String canonical) {
        aliases.put(ComponentUtils.keyStandard(alias), ComponentUtils.keyStandard(canonical));
    }

    public boolean supports(String rawKey) {
        return resolve(ComponentUtils.keyStandard(rawKey)) != null;
    }

    // ---------------------------------------------------------------- 应用

    public void apply(ItemStack item, JsonNode components, ComponentContext context) {
        if (item == null || components == null || !components.isObject()) {
            return;
        }

        Map<String, JsonNode> normalized = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = components.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            normalized.put(ComponentUtils.keyStandard(entry.getKey()), entry.getValue());
        }

        // 名称相关字段（display-name / prefix / suffix）彼此有优先级，单独处理
        applyName(item, normalized);

        normalized.forEach((key, value) -> {
            if (NAME_KEYS.contains(key) || EXTERNAL_KEYS.contains(key)) {
                return;
            }
            Applier applier = resolve(key);
            if (applier == null) {
                logger.warning("Unknown data component '" + key + "', ignored.");
                return;
            }
            try {
                applier.apply(item, value, context);
            } catch (RuntimeException e) {
                logger.warning("Failed to apply data component '" + key + "': " + e.getMessage());
            }
        });
    }

    private Applier resolve(String key) {
        if (key == null) {
            return null;
        }
        Applier direct = appliers.get(key);
        if (direct != null) {
            return direct;
        }
        String canonical = aliases.get(key);
        return canonical == null ? null : appliers.get(canonical);
    }

    // ------------------------------------------------------------ 名称处理

    private void applyName(ItemStack item, Map<String, JsonNode> components) {
        JsonNode display = firstPresent(components, "display_name", "custom_name", "name");
        if (display != null) {
            item.setData(DataComponentTypes.CUSTOM_NAME, ComponentBuilder.text(display.asText()));
            return;
        }

        JsonNode prefix = components.get("prefix");
        JsonNode suffix = components.get("suffix");
        if (prefix == null && suffix == null) {
            return;
        }

        Component name = currentName(item);
        if (prefix != null && !prefix.isNull()) {
            name = ComponentBuilder.text(prefix.asText()).append(name);
        }
        if (suffix != null && !suffix.isNull()) {
            name = name.append(ComponentBuilder.text(suffix.asText()));
        }
        item.setData(DataComponentTypes.CUSTOM_NAME, name);
    }

    private static JsonNode firstPresent(Map<String, JsonNode> components, String... keys) {
        for (String key : keys) {
            JsonNode value = components.get(key);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private static Component currentName(ItemStack item) {
        Component customName = item.getData(DataComponentTypes.CUSTOM_NAME);
        if (customName != null) {
            return customName;
        }
        String translationKey = item.getType().getItemTranslationKey();
        return Component.translatable(translationKey == null ? "" : translationKey);
    }

    // ------------------------------------------------------------ 显式注册

    private void registerDefaults() {
        register(DataComponentTypes.LORE, (value, item, ctx) -> {
            List<Component> lines = new ArrayList<>();
            for (String line : ComponentUtils.strings(value)) {
                lines.add(ComponentBuilder.text(line));
            }
            return ComponentBuilder.lore(lines);
        });

        register(DataComponentTypes.ENCHANTMENTS, (value, item, ctx) -> parseEnchantments(value, item, ctx, true));
        register(DataComponentTypes.STORED_ENCHANTMENTS, (value, item, ctx) -> parseEnchantments(value, item, ctx, false));

        register(DataComponentTypes.ATTRIBUTE_MODIFIERS, (value, item, ctx) -> parseAttributes(value, ctx));

        register(DataComponentTypes.CUSTOM_MODEL_DATA, (value, item, ctx) -> parseCustomModelData(value));

        register(DataComponentTypes.ITEM_MODEL, (value, item, ctx) -> {
            String raw = value.asText();
            return raw.contains(":") ? Key.key(raw) : Key.key(Key.MINECRAFT_NAMESPACE, raw);
        });

        register(DataComponentTypes.MAX_DAMAGE, (value, item, ctx) -> value.asInt());
        register(DataComponentTypes.DAMAGE, (value, item, ctx) -> value.asInt());
        register(DataComponentTypes.MAX_STACK_SIZE, (value, item, ctx) -> value.asInt());
        register(DataComponentTypes.REPAIR_COST, (value, item, ctx) -> value.asInt());
        register(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, (value, item, ctx) -> value.asBoolean());
        registerNonValued(DataComponentTypes.UNBREAKABLE);

        register(DataComponentTypes.RARITY, (value, item, ctx) -> parseRarity(value.asText()));
        register(DataComponentTypes.EQUIPPABLE, (value, item, ctx) -> parseEquippable(value));

        register(DataComponentTypes.TOOLTIP_DISPLAY, (value, item, ctx) -> {
            // 组件里存的是"要隐藏什么"，跟配置里写 true 想表达的"正常显示"正好相反；
            // 对象形态则直接看原版的 hide_tooltip
            boolean hide = value.isBoolean() ? !value.asBoolean() : ComponentUtils.bool(value, "hide_tooltip", false);
            return ComponentBuilder.tooltipDisplay().hideTooltip(hide).build();
        });

        register(DataComponentTypes.USE_EFFECTS, (value, item, ctx) -> ComponentBuilder.useEffects(
                ComponentUtils.bool(value, "can-sprint", true),
                ComponentUtils.bool(value, "interact-vibrations", true),
                (float) ComponentUtils.doubleValue(value, "speed-multiplier", 1.0D)));

    }

    private static ItemEnchantments parseEnchantments(JsonNode value, ItemStack item, ComponentContext context,
                                                      boolean mergeExisting) {
        // 普通附魔是叠加语义，得保留物品上已有的；stored-enchantments 是独立的一栏，从空开始
        Map<Enchantment, Integer> merged = mergeExisting
                ? new LinkedHashMap<>(item.getEnchantments())
                : new LinkedHashMap<>();

        if (value.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                Enchantment enchantment = lookupEnchantment(entry.getKey());
                if (enchantment == null) {
                    context.logger().warning("Ignored unknown enchantment: " + entry.getKey());
                    continue;
                }
                merged.put(enchantment, entry.getValue().asInt(1));
            }
        } else {
            for (String raw : ComponentUtils.strings(value)) {
                int separator = raw.lastIndexOf(':');
                if (separator <= 0) {
                    context.logger().warning("Ignored malformed enchantment entry: " + raw);
                    continue;
                }
                Enchantment enchantment = lookupEnchantment(raw.substring(0, separator).trim());
                if (enchantment == null) {
                    context.logger().warning("Ignored unknown enchantment: " + raw);
                    continue;
                }
                try {
                    merged.put(enchantment, Integer.parseInt(raw.substring(separator + 1).trim()));
                } catch (NumberFormatException e) {
                    context.logger().warning("Ignored illegal enchantment level: " + raw);
                }
            }
        }

        return ItemEnchantments.itemEnchantments(merged);
    }

    private static ItemAttributeModifiers parseAttributes(JsonNode value, ComponentContext context) {
        ItemAttributeModifiers.Builder builder = ComponentBuilder.attributes();
        if (!value.isArray()) {
            return builder.build();
        }

        int index = 0;
        for (JsonNode entry : value) {
            index++;
            String rawType = ComponentUtils.text(entry, "type");
            Attribute attribute = resolveAttribute(rawType);
            if (attribute == null) {
                context.logger().warning("Ignored unknown attribute: " + rawType);
                continue;
            }
            JsonNode amount = entry.get("amount");
            if (amount == null || !amount.isNumber()) {
                context.logger().warning("Invalid amount for attribute " + rawType + ": " + amount);
                continue;
            }

            NamespacedKey modifierKey = new NamespacedKey(context.plugin(), context.keyPrefix() + "_attr_" + index);
            AttributeModifier modifier = new AttributeModifier(
                    modifierKey, amount.doubleValue(), resolveOperation(ComponentUtils.text(entry, "operation")));
            builder.addModifier(attribute, modifier, resolveSlotGroup(ComponentUtils.text(entry, "slot")));
        }
        return builder.build();
    }

    private static CustomModelData parseCustomModelData(JsonNode value) {
        CustomModelData.Builder builder = ComponentBuilder.customModelData();
        if (value.isNumber()) {
            return builder.addFloat((float) value.asDouble()).build();
        }
        if (value.isObject()) {
            // 原版 CODEC 的形态：{floats: [...], flags: [...], strings: [...]}
            addModelEntries(builder, value.get("floats"));
            addModelEntries(builder, value.get("flags"));
            addModelEntries(builder, value.get("strings"));
            return builder.build();
        }
        addModelEntries(builder, value);
        return builder.build();
    }

    /** 按元素类型分派到 floats / flags / strings 三个通道。 */
    private static void addModelEntries(CustomModelData.Builder builder, JsonNode node) {
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode entry : node) {
            if (entry.isNumber()) {
                builder.addFloat((float) entry.asDouble());
            } else if (entry.isBoolean()) {
                builder.addFlag(entry.asBoolean());
            } else {
                builder.addString(entry.asText());
            }
        }
    }


    private static Equippable parseEquippable(JsonNode value) {
        if (value.isBoolean()) {
            return value.asBoolean() ? ComponentBuilder.equippable(EquipmentSlot.HEAD).build() : null;
        }
        String raw = value.isObject() ? ComponentUtils.text(value, "slot") : value.asText();
        EquipmentSlot slot = resolveSlot(raw);
        return slot == null ? null : ComponentBuilder.equippable(slot).build();
    }

    private static EquipmentSlot resolveSlot(String raw) {
        if (raw == null || raw.isBlank()) {
            return EquipmentSlot.HEAD;
        }
        try {
            return EquipmentSlot.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ItemRarity parseRarity(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ItemRarity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Attribute resolveAttribute(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        Attribute attribute = lookupAttribute(normalized);
        if (attribute != null) {
            return attribute;
        }
        for (String legacy : LEGACY_ATTRIBUTE_PREFIXES) {
            if (normalized.startsWith(legacy)) {
                return lookupAttribute(normalized.substring(legacy.length()));
            }
        }
        return null;
    }

    private static Attribute lookupAttribute(String name) {
        NamespacedKey key = ComponentUtils.parseKey(null, name);
        return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE).get(key);
    }

    private static Enchantment lookupEnchantment(String name) {
        NamespacedKey key = ComponentUtils.parseKey(null, name);
        return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
    }

    private static AttributeModifier.Operation resolveOperation(String raw) {
        if (raw != null && !raw.isBlank()) {
            String name = raw.trim().toUpperCase(Locale.ROOT);
            try {
                return AttributeModifier.Operation.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // 原版 CODEC 的枚举名跟 Bukkit 这套不同名，对上号才不会默默按"加法"处理
                switch (name) {
                    case "ADD_VALUE":
                        return AttributeModifier.Operation.ADD_NUMBER;
                    case "ADD_MULTIPLIED_BASE":
                        return AttributeModifier.Operation.ADD_SCALAR;
                    case "ADD_MULTIPLIED_TOTAL":
                        return AttributeModifier.Operation.MULTIPLY_SCALAR_1;
                    default:
                        break;
                }
            }
        }
        return AttributeModifier.Operation.ADD_NUMBER;
    }

    private static EquipmentSlotGroup resolveSlotGroup(String raw) {
        if (raw != null && !raw.isBlank()) {
            EquipmentSlotGroup group = EquipmentSlotGroup.getByName(raw.trim());
            if (group != null) {
                return group;
            }
        }
        return EquipmentSlotGroup.ANY;
    }

    // -------------------------------------------------------------- 自动层


    /** 值类型是注册表条目的组件（如伤害类型、生物变体）：把配置里的 key 直接查注册表。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerRegistryLookup(DataComponentType.Valued<?> type, RegistryKey<?> registryKey) {
        DataComponentType.Valued rawType = type;
        RegistryKey rawRegistryKey = registryKey;
        appliers.put(keyOf(type), (item, value, context) -> {
            NamespacedKey key = ComponentUtils.parseKey(null, value.asText());
            if (key == null) {
                return;
            }
            Registry<?> registry = RegistryAccess.registryAccess().getRegistry(rawRegistryKey);
            Object entry = registry.get(key);
            if (entry != null) {
                item.setData(rawType, entry);
            }
        });
    }

    private int registerAutomatic() {
        int count = 0;
        for (Field field : DataComponentTypes.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isPublic(field.getModifiers())) {
                continue;
            }
            Object raw;
            try {
                raw = field.get(null);
            } catch (IllegalAccessException e) {
                continue;
            }
            if (!(raw instanceof DataComponentType type)) {
                continue;
            }
            if (appliers.containsKey(keyOf(type))) {
                continue;
            }
            if (raw instanceof DataComponentType.NonValued nonValued) {
                registerNonValued(nonValued);
                count++;
            } else if (raw instanceof DataComponentType.Valued<?> valued) {
                Class<?> valueClass = genericValueClass(field);
                RegistryKey<?> registryKey = valueClass == null ? null : ComponentParsers.registryKeyFor(valueClass);
                if (registryKey != null) {
                    registerRegistryLookup(valued, registryKey);
                    count++;
                } else if (ComponentConstructor.supports(valueClass)) {
                    registerAutomatic(valued, valueClass);
                    count++;
                }
            }
        }
        return count;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerAutomatic(DataComponentType.Valued<?> type, Class<?> valueClass) {
        DataComponentType.Valued rawType = type;
        appliers.put(keyOf(type), (item, value, context) -> {
            Object decoded = ComponentConstructor.construct(value, valueClass, context);
            if (decoded != null) {
                item.setData(rawType, decoded);
            }
        });
    }

    /** 从字段声明里取出 {@code Valued<X>} 的 {@code X}。取不到返回 null。 */
    private static Class<?> genericValueClass(Field field) {
        try {
            if (field.getAnnotatedType() instanceof AnnotatedParameterizedType annotated) {
                Type argument = annotated.getAnnotatedActualTypeArguments()[0].getType();
                return ComponentUtils.toClass(argument);
            }
        } catch (RuntimeException ignored) {
            // 拿不到泛型实参就放弃自动注册，交给显式层
        }
        return null;
    }

    private static String keyOf(DataComponentType type) {
        return ComponentUtils.keyStandard(type.getKey().value());
    }
}









