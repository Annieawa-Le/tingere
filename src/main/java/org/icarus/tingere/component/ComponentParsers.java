package org.icarus.tingere.component;

import com.fasterxml.jackson.databind.JsonNode;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.util.TriState;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;
import org.bukkit.block.banner.PatternType;
import org.bukkit.damage.DamageType;
import org.bukkit.inventory.ItemType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 复合组件共享的解析辅助。
 */
@SuppressWarnings("UnstableApiUsage")
public final class ComponentParsers {

    private static final Map<Class<?>, RegistryKey<?>> REGISTRY_BY_VALUE = buildRegistryIndex();

    private ComponentParsers() {
    }

    // ------------------------------------------------------------ key / tag

    /** {@code "minecraft:item.shield.block"} 或 {@code "item.shield.block"} → Key。 */
    static Key key(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        return value.contains(":") ? Key.key(value) : Key.key(Key.MINECRAFT_NAMESPACE, value);
    }

    /**
     * 构造注册表标签引用。写法可以是 {@code "#minecraft:is_fire"}，也可以是
     * {@code "minecraft:is_fire"} 甚至 {@code "is_fire"}，井号会被剥掉。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static TagKey<?> tagKey(RegistryKey<?> registryKey, String raw) {
        if (registryKey == null || raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        NamespacedKey namespaced = ComponentUtils.parseKey(null, value);
        if (namespaced == null) {
            return null;
        }
        return TagKey.create((RegistryKey) registryKey, Key.key(namespaced.getNamespace(), namespaced.getKey()));
    }

    /**
     * 把通配的 TagKey 收窄成 builder 要的具体泛型。
     * <p>
     * Paper 的入口签名是 {@code bypassedBy(TagKey<DamageType>)}，而标签是从配置里
     * 解析出来的、编译期不知道元素类型——这层转换收在这里，不扩散到注册表。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static TagKey<DamageType> damageTypeTag(TagKey<?> tag) {
        return (TagKey) tag;
    }

    /** BannerPattern 的标签引用（provides_banner_patterns 用）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static TagKey<PatternType> bannerPatternTag(String raw) {
        return (TagKey) tagKey(RegistryKey.BANNER_PATTERN, raw);
    }

    // --------------------------------------------------------------- 基础值

    /** {@code "#ff8800"} / {@code "ff8800"} / {@code 16738048} → Color。 */
    static Color color(JsonNode node, ComponentContext context) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return Color.fromRGB(node.asInt() & 0xFFFFFF);
        }
        String raw = node.asText("").trim();
        if (raw.isEmpty()) {
            return null;
        }
        if (raw.startsWith("#")) {
            raw = raw.substring(1);
        }
        if (raw.startsWith("0x") || raw.startsWith("0X")) {
            raw = raw.substring(2);
        }
        try {
            return Color.fromRGB(Integer.parseInt(raw, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            context.logger().warning("Ignored malformed color: " + node);
            return null;
        }
    }

    /** 三态：缺省返回 {@code NOT_SET}，供"不显式指定就不写该字段"的组件使用。 */
    static TriState triState(JsonNode node) {
        if (node == null || node.isNull()) {
            return TriState.NOT_SET;
        }
        if (node.isBoolean()) {
            return TriState.byBoolean(node.asBoolean());
        }
        String raw = node.asText("").trim();
        if ("true".equalsIgnoreCase(raw)) {
            return TriState.TRUE;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return TriState.FALSE;
        }
        return TriState.NOT_SET;
    }

    /** 无论是单个值还是列表，都摊平成列表。 */
    static List<JsonNode> array(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        if (node == null || node.isNull()) {
            return result;
        }
        if (node.isArray()) {
            node.forEach(result::add);
        } else {
            result.add(node);
        }
        return result;
    }

    /** 依次取第一个非空字段的文本值。 */
    static String firstText(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            String value = ComponentUtils.text(node, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ 枚举/注册表

    /**
     * 按"枚举名 → 注册表 key"的顺序解析一个游戏对象。
     *
     * @param label 出错时写进日志的字段含义，例如 {@code "attribute"}、{@code "banner pattern"}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> T enumOrRegistry(Class<T> type, String raw, ComponentContext context, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (type.isEnum()) {
            try {
                return type.cast(Enum.valueOf((Class<Enum>) type, value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // 不是枚举名，继续按注册表 key 找（1.21 里不少"枚举"已经改成了注册表条目）
            }
        }
        Object found = lookupRegistryValue(type, value);
        if (type.isInstance(found)) {
            return type.cast(found);
        }
        context.logger().warning("Ignored unknown " + label + ": " + raw);
        return null;
    }

    /** 在注册表里按名字取一个值对象；找不到返回 null。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object lookupRegistryValue(Class<?> valueType, String name) {
        RegistryKey key = REGISTRY_BY_VALUE.get(valueType);
        if (key == null) {
            return null;
        }
        NamespacedKey namespaced = ComponentUtils.parseKey(null, name);
        if (namespaced == null) {
            return null;
        }
        Registry registry = RegistryAccess.registryAccess().getRegistry(key);
        return registry.get(namespaced);
    }

    static RegistryKey<?> registryKeyFor(Class<?> valueType) {
        return REGISTRY_BY_VALUE.get(valueType);
    }

    /** 反射 {@link RegistryKey} 的静态常量，建立"值类型 → 注册表"索引。 */
    private static Map<Class<?>, RegistryKey<?>> buildRegistryIndex() {
        Map<Class<?>, RegistryKey<?>> index = new HashMap<>();
        for (Field field : RegistryKey.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isPublic(field.getModifiers())) {
                continue;
            }
            Object value;
            try {
                value = field.get(null);
            } catch (IllegalAccessException e) {
                continue;
            }
            if (value instanceof RegistryKey<?> key) {
                Class<?> valueType = genericValueClass(field);
                if (valueType != null) {
                    index.putIfAbsent(valueType, key);
                }
            }
        }
        return index;
    }

    /** 从字段声明里取出 {@code RegistryKey<X>} 的 {@code X}。取不到返回 null。 */
    private static Class<?> genericValueClass(Field field) {
        try {
            if (field.getAnnotatedType() instanceof AnnotatedParameterizedType annotated) {
                Type argument = annotated.getAnnotatedActualTypeArguments()[0].getType();
                return ComponentUtils.toClass(argument);
            }
        } catch (RuntimeException ignored) {
            // 拿不到泛型实参就放弃索引，调用方退回显式写法
        }
        return null;
    }

    // ---------------------------------------------------------------- 集合

    /** 方块 id 列表 → 注册表集合（Tool 的 rules、盾牌的伤害减免都用它）。 */
    /**
     * 方块集合，三种写法都认：
     * <ul>
     *   <li>{@code [stone, dirt]} —— 项目自己的简写；</li>
     *   <li>{@code {blocks: [stone]}} 与 {@code [{blocks: [stone]}]} —— 原版 predicate 的形态。
     *       这是 {@code can_place_on} / {@code can_break} 从物品导出时的样子：原版把它建模成
     *       {@code List<BlockPredicate>}，允许一个组件挂多组条件。</li>
     * </ul>
     * 原版 predicate 还能带 nbt / properties 做更细的匹配，这里只取方块集合，其余成分丢弃。
     */
    static RegistryKeySet<?> blockSet(JsonNode node, ComponentContext context) {
        List<BlockType> types = new ArrayList<>();
        for (String raw : blockNames(node)) {
            String value = raw.trim();
            if (value.startsWith("#")) {
                context.logger().warning("Block tags are not supported here yet, ignored: " + raw);
                continue;
            }
            Material material = Material.matchMaterial(value);
            if (material == null || !material.isBlock()) {
                context.logger().warning("Ignored unknown block: " + raw);
                continue;
            }
            types.add(material.asBlockType());
        }
        return types.isEmpty() ? null : RegistrySet.keySetFromValues(RegistryKey.BLOCK, types);
    }

    /**
     * 从各种形态里把方块 id 挖出来：嵌套层级不固定（简写是平的，原版 predicate 至少多一层
     * {@code blocks}，而且是数组里套对象），所以直接递归收叶子上的字符串。
     * 对象里只认 {@code blocks} 字段——predicate 的 nbt / properties 不是方块名。
     */
    private static List<String> blockNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        collectBlockNames(node, names);
        return names;
    }

    private static void collectBlockNames(JsonNode node, List<String> names) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode entry : node) {
                collectBlockNames(entry, names);
            }
            return;
        }
        if (node.isObject()) {
            JsonNode blocks = node.get("blocks");
            if (blocks != null) {
                collectBlockNames(blocks, names);
            }
            return;
        }
        names.add(node.asText());
    }

    /** 物品 id 列表 → 注册表集合（repairable 的修复材料用它）。 */
    static RegistryKeySet<?> itemSet(JsonNode node, ComponentContext context) {
        List<ItemType> types = new ArrayList<>();
        for (String raw : ComponentUtils.strings(node)) {
            ItemType itemType = itemType(raw, context);
            if (itemType != null) {
                types.add(itemType);
            }
        }
        return types.isEmpty() ? null : RegistrySet.keySetFromValues(RegistryKey.ITEM, types);
    }

    /** 伤害类型 id 列表 → 注册表集合。 */
    static RegistryKeySet<?> damageTypeSet(JsonNode node, ComponentContext context) {
        List<DamageType> types = new ArrayList<>();
        for (String raw : ComponentUtils.strings(node)) {
            Object value = lookupRegistryValue(DamageType.class, raw.trim());
            if (value instanceof DamageType type) {
                types.add(type);
            } else {
                context.logger().warning("Ignored unknown damage type: " + raw);
            }
        }
        return types.isEmpty() ? null : RegistrySet.keySetFromValues(RegistryKey.DAMAGE_TYPE, types);
    }

    /** 药水效果类型 id 列表 → 注册表集合（ConsumeEffect 的移除效果用它）。 */
    static RegistryKeySet<?> effectSet(JsonNode node, ComponentContext context) {
        List<PotionEffectType> types = new ArrayList<>();
        for (String raw : ComponentUtils.strings(node)) {
            Object value = lookupRegistryValue(PotionEffectType.class, raw.trim());
            if (value instanceof PotionEffectType type) {
                types.add(type);
            } else {
                context.logger().warning("Ignored unknown potion effect: " + raw);
            }
        }
        return types.isEmpty() ? null : RegistrySet.keySetFromValues(RegistryKey.MOB_EFFECT, types);
    }

    static ItemType itemType(String raw, ComponentContext context) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(raw.trim());
        if (material == null || material.isAir()) {
            context.logger().warning("Ignored unknown item: " + raw);
            return null;
        }
        return material.asItemType();
    }

    // ------------------------------------------------------------ 药水效果

    /**
     * 药水效果列表。每一项支持两种写法：
     * <pre>
     * - speed:200:1        # 效果:持续刻数:等级
     * - {type: speed, duration: 200, amplifier: 1}
     * </pre>
     */
    static List<PotionEffect> potionEffects(JsonNode node, ComponentContext context) {
        List<PotionEffect> effects = new ArrayList<>();
        for (JsonNode entry : array(node)) {
            PotionEffect effect = potionEffect(entry, context);
            if (effect != null) {
                effects.add(effect);
            }
        }
        return effects;
    }

    static PotionEffect potionEffect(JsonNode entry, ComponentContext context) {
        String rawType;
        int duration = 200;
        int amplifier = 0;

        if (entry.isObject()) {
            rawType = firstText(entry, "type", "effect");
            duration = ComponentUtils.intValue(entry, "duration", duration);
            amplifier = ComponentUtils.intValue(entry, "amplifier", amplifier);
        } else {
            String[] parts = entry.asText("").trim().split("[:\\s]+");
            if (parts.length == 0 || parts[0].isBlank()) {
                return null;
            }
            rawType = parts[0];
            if (parts.length > 1) {
                duration = parseInt(parts[1], duration, context, "potion duration");
            }
            if (parts.length > 2) {
                amplifier = parseInt(parts[2], amplifier, context, "potion amplifier");
            }
        }

        Object type = lookupRegistryValue(PotionEffectType.class, rawType == null ? "" : rawType.trim());
        if (!(type instanceof PotionEffectType potionType)) {
            context.logger().warning("Ignored unknown potion effect: " + rawType);
            return null;
        }
        return new PotionEffect(potionType, Math.max(1, duration), Math.max(0, amplifier));
    }

    // ------------------------------------------------------------ 消耗效果

    /**
     * 消耗品效果。type 可取：
     * {@code apply-effects} / {@code remove-effects} / {@code clear-all-effects}
     * / {@code teleport-randomly} / {@code play-sound}。
     */
    static ConsumeEffect consumeEffect(JsonNode node, ComponentContext context) {
        if (!node.isObject()) {
            context.logger().warning("Ignored malformed consume effect: " + node);
            return null;
        }
        String type = ComponentUtils.keyStandard(firstText(node, "type", "effect"));
        if (type == null) {
            context.logger().warning("Consume effect is missing 'type'.");
            return null;
        }
        return switch (type) {
            case "apply_effects", "apply_status_effects" -> ComponentBuilder.applyStatusEffects(
                    potionEffects(node.get("effects"), context),
                    (float) ComponentUtils.doubleValue(node, "probability", 1.0D));
            case "remove_effects", "remove_status_effects" -> {
                RegistryKeySet<?> effects = effectSet(node.get("effects"), context);
                yield effects == null ? null : ComponentBuilder.removeStatusEffects(effects);
            }
            case "clear_all_effects", "clear_all_status_effects" -> ComponentBuilder.clearAllStatusEffects();
            case "teleport_randomly" -> ComponentBuilder.teleportRandomly(
                    (float) ComponentUtils.doubleValue(node, "diameter", 16.0D));
            case "play_sound" -> {
                Key sound = key(ComponentUtils.text(node, "sound"));
                yield sound == null ? null : ComponentBuilder.playSound(sound);
            }
            default -> {
                context.logger().warning("Ignored unknown consume effect type: " + type);
                yield null;
            }
        };
    }

    // ---------------------------------------------------------------- 烟花

    /** 单发烟花效果（{@code firework_explosion} 与 {@code fireworks} 的条目共用）。 */
    static FireworkEffect fireworkEffect(JsonNode node, ComponentContext context) {
        if (node == null || !node.isObject()) {
            context.logger().warning("Ignored malformed firework effect: " + node);
            return null;
        }
        FireworkEffect.Builder builder = ComponentBuilder.fireworkEffect();
        FireworkEffect.Type type = enumOrRegistry(
                FireworkEffect.Type.class, ComponentUtils.text(node, "type"), context, "firework shape");
        builder.with(type == null ? FireworkEffect.Type.BALL : type);

        for (JsonNode color : array(node.get("colors"))) {
            Color parsed = color(color, context);
            if (parsed != null) {
                builder.withColor(parsed);
            }
        }
        for (JsonNode color : array(node.get("fade"))) {
            Color parsed = color(color, context);
            if (parsed != null) {
                builder.withFade(parsed);
            }
        }
        builder.flicker(ComponentUtils.bool(node, "flicker", false));
        builder.trail(ComponentUtils.bool(node, "trail", false));
        return builder.build();
    }

    // ---------------------------------------------------------------- 杂项

    static int parseInt(String raw, int fallback, ComponentContext context, String label) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            context.logger().warning("Ignored malformed " + label + ": " + raw);
            return fallback;
        }
    }
}


