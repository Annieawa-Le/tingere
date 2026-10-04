package org.icarus.tingere.component;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import io.papermc.paper.potion.SuspiciousEffectEntry;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.JukeboxSong;
import org.bukkit.Location;
import org.bukkit.MusicInstrument;
import org.bukkit.World;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.map.MapCursor;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 需要 builder 才能构造的复合组件，全部在这里注册。
 * <p>
 * 这些组件的值不是"一串数字或一个名字"，而是原版需要用 builder 拼出来的结构，
 * 所以没法交给自动层，必须显式写解码逻辑。拆分到独立文件只是为了让
 * {@link ComponentRegistry} 保持"表 + 分派"的本分——注册这件事本身换个地方做而已。
 * <p>
 * 你真的以为我特别想写这个雷霆大类吗。。
 */
@SuppressWarnings("UnstableApiUsage")
final class ComponentComposites {

    private ComponentComposites() {
    }

    static void registerAll(ComponentRegistry registry) {
        registerFood(registry);
        registerCombat(registry);
        registerAppearance(registry);
        registerContainers(registry);
        registerMisc(registry);
        registerAliases(registry);
    }

    // ------------------------------------------------------------ 食物与使用

    private static void registerFood(ComponentRegistry registry) {
        registry.register(DataComponentTypes.FOOD, (value, item, ctx) -> ComponentBuilder.food(
                ComponentUtils.intValue(value, "nutrition", 0),
                (float) ComponentUtils.doubleValue(value, "saturation", 0.0D),
                ComponentUtils.bool(value, "can-always-eat", false)));

        registry.register(DataComponentTypes.CONSUMABLE, (value, item, ctx) -> {
            var builder = ComponentBuilder.consumable();
            builder.consumeSeconds((float) ComponentUtils.doubleValue(value, "seconds", 1.6D));
            builder.hasConsumeParticles(ComponentUtils.bool(value, "particles", true));

            ItemUseAnimation animation = ComponentParsers.enumOrRegistry(
                    ItemUseAnimation.class, ComponentUtils.text(value, "animation"), ctx, "consume animation");
            if (animation != null) {
                builder.animation(animation);
            }
            Key sound = ComponentParsers.key(ComponentUtils.text(value, "sound"));
            if (sound != null) {
                builder.sound(sound);
            }
            for (JsonNode entry : ComponentParsers.array(value.get("effects"))) {
                ConsumeEffect effect = ComponentParsers.consumeEffect(entry, ctx);
                if (effect != null) {
                    builder.addEffect(effect);
                }
            }
            return builder.build();
        });

        registry.register(DataComponentTypes.DEATH_PROTECTION, (value, item, ctx) -> {
            var builder = ComponentBuilder.deathProtection();
            for (JsonNode entry : ComponentParsers.array(value)) {
                ConsumeEffect effect = ComponentParsers.consumeEffect(entry, ctx);
                if (effect != null) {
                    builder.addEffect(effect);
                }
            }
            return builder.build();
        });

        registry.register(DataComponentTypes.POTION_CONTENTS, (value, item, ctx) -> {
            var builder = ComponentBuilder.potionContents();

            PotionType potionType = ComponentParsers.enumOrRegistry(PotionType.class,
                    ComponentParsers.firstText(value, "potion", "type"), ctx, "potion type");
            if (potionType != null) {
                builder.potion(potionType);
            }
            Color color = ComponentParsers.color(value.get("color"), ctx);
            if (color != null) {
                builder.customColor(color);
            }
            String customName = ComponentUtils.text(value, "custom-name");
            if (customName != null) {
                builder.customName(customName);
            }
            builder.addCustomEffects(ComponentParsers.potionEffects(value.get("effects"), ctx));
            return builder.build();
        });

        registry.register(DataComponentTypes.USE_COOLDOWN, (value, item, ctx) -> {
            float seconds;
            Key group = null;
            if (value.isObject()) {
                seconds = (float) ComponentUtils.doubleValue(value, "seconds", 1.0D);
                group = ComponentParsers.key(ComponentUtils.text(value, "group"));
            } else {
                seconds = (float) value.asDouble(1.0D);
            }
            var builder = ComponentBuilder.useCooldown(seconds);
            if (group != null) {
                builder.cooldownGroup(group);
            }
            return builder.build();
        });

        registry.register(DataComponentTypes.USE_REMAINDER, (value, item, ctx) -> {
            var remainder = ItemStackCodec.parse(value, ctx);
            return remainder == null ? ComponentBuilder.useRemainder(item) : ComponentBuilder.useRemainder(remainder);
        });

        registry.register(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS, (value, item, ctx) -> {
            var builder = ComponentBuilder.suspiciousStewEffects();
            for (JsonNode entry : ComponentParsers.array(value)) {
                String rawEffect = entry.isObject()
                        ? ComponentParsers.firstText(entry, "effect", "type")
                        : entry.asText();
                int duration = entry.isObject() ? ComponentUtils.intValue(entry, "duration", 160) : 160;

                Object type = ComponentParsers.lookupRegistryValue(PotionEffectType.class,
                        rawEffect == null ? "" : rawEffect.trim());
                if (type instanceof PotionEffectType effectType) {
                    builder.add(SuspiciousEffectEntry.create(effectType, Math.max(1, duration)));
                } else {
                    ctx.logger().warning("Ignored unknown stew effect: " + rawEffect);
                }
            }
            return builder.build();
        });
    }

    // ---------------------------------------------------------------- 战斗

    private static void registerCombat(ComponentRegistry registry) {
        registry.register(DataComponentTypes.TOOL, (value, item, ctx) -> {
            var builder = ComponentBuilder.tool();
            builder.defaultMiningSpeed((float) ComponentUtils.doubleValue(value, "default-mining-speed", 1.0D));
            builder.damagePerBlock(ComponentUtils.intValue(value, "damage-per-block", 1));
            builder.canDestroyBlocksInCreative(ComponentUtils.bool(value, "can-destroy-blocks-in-creative", true));

            for (JsonNode entry : ComponentParsers.array(value.get("rules"))) {
                var blocks = ComponentParsers.blockSet(entry.get("blocks"), ctx);
                if (blocks == null) {
                    continue;
                }
                float speed = entry.hasNonNull("speed") ? (float) entry.get("speed").asDouble() : 1.0F;
                builder.addRule(ComponentBuilder.toolRule(
                        blocks, speed, ComponentParsers.triState(entry.get("correct-for-drops"))));
            }
            return builder.build();
        });

        registry.register(DataComponentTypes.WEAPON, (value, item, ctx) -> ComponentBuilder.weapon()
                .itemDamagePerAttack(ComponentUtils.intValue(value, "item-damage-per-attack", 1))
                .disableBlockingForSeconds((float) ComponentUtils.doubleValue(
                        value, "disable-blocking-for-seconds", 0.0D))
                .build());

        registry.register(DataComponentTypes.ATTACK_RANGE, (value, item, ctx) -> ComponentBuilder.attackRange()
                .minReach((float) ComponentUtils.doubleValue(value, "min-reach", 0.0D))
                .maxReach((float) ComponentUtils.doubleValue(value, "max-reach", 3.0D))
                .minCreativeReach((float) ComponentUtils.doubleValue(value, "min-creative-reach", 0.0D))
                .maxCreativeReach((float) ComponentUtils.doubleValue(value, "max-creative-reach", 5.0D))
                .hitboxMargin((float) ComponentUtils.doubleValue(value, "hitbox-margin", 0.0D))
                .mobFactor((float) ComponentUtils.doubleValue(value, "mob-factor", 1.0D))
                .build());

        registry.register(DataComponentTypes.BLOCKS_ATTACKS, (value, item, ctx) -> {
            var builder = ComponentBuilder.blocksAttacks();
            builder.blockDelaySeconds((float) ComponentUtils.doubleValue(value, "block-delay-seconds", 0.0D));
            builder.disableCooldownScale((float) ComponentUtils.doubleValue(value, "disable-cooldown-scale", 1.0D));

            Key blockSound = ComponentParsers.key(ComponentUtils.text(value, "block-sound"));
            if (blockSound != null) {
                builder.blockSound(blockSound);
            }
            Key disableSound = ComponentParsers.key(ComponentUtils.text(value, "disable-sound"));
            if (disableSound != null) {
                builder.disableSound(disableSound);
            }
            var bypassedBy = ComponentParsers.tagKey(RegistryKey.DAMAGE_TYPE,
                    ComponentUtils.text(value, "bypassed-by"));
            if (bypassedBy != null) {
                builder.bypassedBy(ComponentParsers.damageTypeTag(bypassedBy));
            }

            JsonNode itemDamage = value.get("item-damage");
            if (itemDamage != null && itemDamage.isObject()) {
                builder.itemDamage(ComponentBuilder.itemDamageFunction()
                        .base((float) ComponentUtils.doubleValue(itemDamage, "base", 1.0D))
                        .factor((float) ComponentUtils.doubleValue(itemDamage, "factor", 1.0D))
                        .threshold((float) ComponentUtils.doubleValue(itemDamage, "threshold", 0.0D))
                        .build());
            }

            for (JsonNode entry : ComponentParsers.array(value.get("damage-reductions"))) {
                var types = ComponentParsers.damageTypeSet(entry.get("type"), ctx);
                builder.addDamageReduction(ComponentBuilder.damageReduction(
                        types,
                        (float) ComponentUtils.doubleValue(entry, "base", 0.0D),
                        (float) ComponentUtils.doubleValue(entry, "factor", 1.0D),
                        (float) ComponentUtils.doubleValue(entry, "horizontal-blocking-angle", 90.0D)));
            }
            return builder.build();
        });

        registry.register(DataComponentTypes.PIERCING_WEAPON, (value, item, ctx) -> {
            var builder = ComponentBuilder.piercingWeapon();
            Key sound = ComponentParsers.key(ComponentUtils.text(value, "sound"));
            if (sound != null) {
                builder.sound(sound);
            }
            Key hitSound = ComponentParsers.key(ComponentUtils.text(value, "hit-sound"));
            if (hitSound != null) {
                builder.hitSound(hitSound);
            }
            builder.dealsKnockback(ComponentUtils.bool(value, "deals-knockback", true));
            builder.dismounts(ComponentUtils.bool(value, "dismounts", false));
            return builder.build();
        });

        registry.register(DataComponentTypes.KINETIC_WEAPON, (value, item, ctx) -> {
            var builder = ComponentBuilder.kineticWeapon();
            builder.damageMultiplier((float) ComponentUtils.doubleValue(value, "damage-multiplier", 1.0D));
            builder.forwardMovement((float) ComponentUtils.doubleValue(value, "forward-movement", 0.0D));
            builder.delayTicks(ComponentUtils.intValue(value, "delay-ticks", 0));
            builder.contactCooldownTicks(ComponentUtils.intValue(value, "contact-cooldown-ticks", 10));

            Key sound = ComponentParsers.key(ComponentUtils.text(value, "sound"));
            if (sound != null) {
                builder.sound(sound);
            }
            Key hitSound = ComponentParsers.key(ComponentUtils.text(value, "hit-sound"));
            if (hitSound != null) {
                builder.hitSound(hitSound);
            }
            setCondition(builder, "damage-conditions", value, ctx);
            setCondition(builder, "knockback-conditions", value, ctx);
            setCondition(builder, "dismount-conditions", value, ctx);
            return builder.build();
        });

        registry.register(DataComponentTypes.ENCHANTABLE, (value, item, ctx) -> {
            int level = value.asInt(0);
            return level <= 0 ? null : ComponentBuilder.enchantable(level);
        });

        registry.register(DataComponentTypes.REPAIRABLE, (value, item, ctx) -> {
            var types = ComponentParsers.itemSet(value, ctx);
            return types == null ? null : ComponentBuilder.repairable(types);
        });

        registry.register(DataComponentTypes.DAMAGE_RESISTANT, (value, item, ctx) -> {
            var tag = ComponentParsers.tagKey(RegistryKey.DAMAGE_TYPE,
                    value.isArray() ? value.get(0).asText() : value.asText());
            return tag == null ? null : ComponentBuilder.damageResistant(tag);
        });
    }

    private static void setCondition(io.papermc.paper.datacomponent.item.KineticWeapon.Builder builder,
            String field, JsonNode value, ComponentContext ctx) {
        JsonNode node = value.get(field);
        if (node == null || !node.isObject()) {
            return;
        }
        var condition = ComponentBuilder.kineticCondition(
                ComponentUtils.intValue(node, "max-duration-ticks", 0),
                (float) ComponentUtils.doubleValue(node, "min-speed", 0.0D),
                (float) ComponentUtils.doubleValue(node, "min-relative-speed", 0.0D));
        switch (field) {
            case "damage-conditions" -> builder.damageConditions(condition);
            case "knockback-conditions" -> builder.knockbackConditions(condition);
            case "dismount-conditions" -> builder.dismountConditions(condition);
            default -> ctx.logger().warning("Unknown kinetic weapon condition: " + field);
        }
    }

    // ------------------------------------------------------------ 装备与外观

    private static void registerAppearance(ComponentRegistry registry) {
        registry.register(DataComponentTypes.DYED_COLOR, (value, item, ctx) -> {
            Color color = ComponentParsers.color(value.isObject() ? value.get("color") : value, ctx);
            return color == null ? null : ComponentBuilder.dyedColor(color);
        });

        registry.register(DataComponentTypes.TRIM, (value, item, ctx) -> {
            Object material = ComponentParsers.lookupRegistryValue(TrimMaterial.class,
                    ComponentUtils.text(value, "material"));
            Object pattern = ComponentParsers.lookupRegistryValue(TrimPattern.class,
                    ComponentUtils.text(value, "pattern"));
            if (!(material instanceof TrimMaterial trimMaterial) || !(pattern instanceof TrimPattern trimPattern)) {
                ctx.logger().warning("'trim' needs both a valid 'material' and 'pattern'.");
                return null;
            }
            return ComponentBuilder.armorTrim(trimMaterial, trimPattern);
        });

        registry.register(DataComponentTypes.BANNER_PATTERNS, (value, item, ctx) -> {
            var builder = ComponentBuilder.bannerPatternLayers();
            for (JsonNode entry : ComponentParsers.array(value)) {
                DyeColor color = ComponentParsers.enumOrRegistry(
                        DyeColor.class, ComponentUtils.text(entry, "color"), ctx, "dye color");
                PatternType pattern = ComponentParsers.enumOrRegistry(
                        PatternType.class, ComponentUtils.text(entry, "pattern"), ctx, "banner pattern");
                if (color == null || pattern == null) {
                    continue;
                }
                builder.add(ComponentBuilder.bannerPattern(color, pattern));
            }
            return builder.build();
        });

        registry.register(DataComponentTypes.POT_DECORATIONS, (value, item, ctx) -> {
            var builder = ComponentBuilder.potDecorations();
            var left = ComponentParsers.itemType(ComponentUtils.text(value, "left"), ctx);
            var right = ComponentParsers.itemType(ComponentUtils.text(value, "right"), ctx);
            var front = ComponentParsers.itemType(ComponentUtils.text(value, "front"), ctx);
            var back = ComponentParsers.itemType(ComponentUtils.text(value, "back"), ctx);
            if (left != null) {
                builder.left(left);
            }
            if (right != null) {
                builder.right(right);
            }
            if (front != null) {
                builder.front(front);
            }
            if (back != null) {
                builder.back(back);
            }
            return builder.build();
        });

        registry.register(DataComponentTypes.INSTRUMENT, (value, item, ctx) -> {
            Object instrument = ComponentParsers.lookupRegistryValue(MusicInstrument.class, value.asText());
            if (instrument instanceof MusicInstrument musicInstrument) {
                return musicInstrument;
            }
            ctx.logger().warning("Ignored unknown instrument: " + value.asText());
            return null;
        });

        registry.register(DataComponentTypes.JUKEBOX_PLAYABLE, (value, item, ctx) -> {
            Object song = ComponentParsers.lookupRegistryValue(JukeboxSong.class, value.asText());
            if (song instanceof JukeboxSong jukeboxSong) {
                return ComponentBuilder.jukeboxPlayable(jukeboxSong).build();
            }
            ctx.logger().warning("Ignored unknown jukebox song: " + value.asText());
            return null;
        });

        registry.register(DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER, (value, item, ctx) ->
                ComponentBuilder.ominousBottleAmplifier(value.asInt(0)));

        registry.register(DataComponentTypes.SWING_ANIMATION, (value, item, ctx) -> {
            var builder = ComponentBuilder.swingAnimation();
            var type = ComponentParsers.enumOrRegistry(io.papermc.paper.datacomponent.item.SwingAnimation.Animation.class,
                    ComponentUtils.text(value, "type"), ctx, "swing animation");
            if (type != null) {
                builder.type(type);
            }
            builder.duration(ComponentUtils.intValue(value, "duration", 6));
            return builder.build();
        });

        registry.register(DataComponentTypes.MAP_ID, (value, item, ctx) -> ComponentBuilder.mapId(value.asInt(0)));

        registry.register(DataComponentTypes.MAP_COLOR, (value, item, ctx) -> {
            Color color = ComponentParsers.color(value.isObject() ? value.get("color") : value, ctx);
            return color == null ? null : ComponentBuilder.mapItemColor().color(color).build();
        });

        registry.register(DataComponentTypes.MAP_DECORATIONS, (value, item, ctx) -> {
            var builder = ComponentBuilder.mapDecorations();
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode node = field.getValue();
                MapCursor.Type type = ComponentParsers.enumOrRegistry(
                        MapCursor.Type.class, ComponentUtils.text(node, "type"), ctx, "map decoration");
                if (type == null) {
                    continue;
                }
                builder.put(field.getKey(), ComponentBuilder.mapDecoration(
                        type,
                        ComponentUtils.doubleValue(node, "x", 0.0D),
                        ComponentUtils.doubleValue(node, "z", 0.0D),
                        (float) ComponentUtils.doubleValue(node, "rotation", 0.0D)));
            }
            return builder.build();
        });
    }

    // ---------------------------------------------------------------- 容器

    private static void registerContainers(ComponentRegistry registry) {
        registry.register(DataComponentTypes.CONTAINER, (value, item, ctx) ->
                ComponentBuilder.containerContents(ItemStackCodec.parseAll(value, ctx)));

        registry.register(DataComponentTypes.BUNDLE_CONTENTS, (value, item, ctx) ->
                ComponentBuilder.bundleContents(ItemStackCodec.parseAll(value, ctx)));

        registry.register(DataComponentTypes.CHARGED_PROJECTILES, (value, item, ctx) ->
                ComponentBuilder.chargedProjectiles(ItemStackCodec.parseAll(value, ctx)));

        registry.register(DataComponentTypes.CONTAINER_LOOT, (value, item, ctx) -> {
            Key lootTable = ComponentParsers.key(ComponentParsers.firstText(value, "table", "loot-table"));
            if (lootTable == null) {
                ctx.logger().warning("'container-loot' needs a 'table'.");
                return null;
            }
            long seed = value.hasNonNull("seed") ? value.get("seed").asLong(0L) : 0L;
            return ComponentBuilder.seededContainerLoot(lootTable).seed(seed).build();
        });

        registry.register(DataComponentTypes.LODESTONE_TRACKER, (value, item, ctx) -> {
            var builder = ComponentBuilder.lodestoneTracker();
            String worldName = ComponentUtils.text(value, "world");
            if (worldName != null) {
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    ctx.logger().warning("Ignored unknown world in 'lodestone-tracker': " + worldName);
                } else {
                    builder.location(new Location(world,
                            ComponentUtils.doubleValue(value, "x", 0.0D),
                            ComponentUtils.doubleValue(value, "y", 0.0D),
                            ComponentUtils.doubleValue(value, "z", 0.0D)));
                }
            }
            builder.tracked(ComponentUtils.bool(value, "tracked", true));
            return builder.build();
        });
    }

    // ---------------------------------------------------------------- 其它

    private static void registerMisc(ComponentRegistry registry) {
        registry.register(DataComponentTypes.PROFILE, (value, item, ctx) -> {
            String name = ComponentUtils.text(value, "name");
            String uuid = ComponentUtils.text(value, "uuid");
            if (name == null && uuid == null && !value.has("properties")) {
                ctx.logger().warning("'profile' needs at least one of 'name', 'uuid' or 'properties'.");
                return null;
            }
            var builder = ComponentBuilder.resolvableProfile();
            if (name != null) {
                builder.name(name);
            }
            if (uuid != null) {
                try {
                    builder.uuid(UUID.fromString(uuid.trim()));
                } catch (IllegalArgumentException e) {
                    ctx.logger().warning("Ignored malformed uuid in 'profile': " + uuid);
                }
            }
            for (JsonNode entry : ComponentParsers.array(value.get("properties"))) {
                String propertyName = ComponentUtils.text(entry, "name");
                String propertyValue = ComponentUtils.text(entry, "value");
                String signature = ComponentUtils.text(entry, "signature");
                if (propertyName == null || propertyValue == null) {
                    continue;
                }
                builder.addProperty(signature == null
                        ? new ProfileProperty(propertyName, propertyValue)
                        : new ProfileProperty(propertyName, propertyValue, signature));
            }
            return builder.build();
        });

        registry.register(DataComponentTypes.FIREWORKS, (value, item, ctx) -> {
            var builder = ComponentBuilder.fireworks();
            builder.flightDuration(ComponentUtils.intValue(value, "flight", 1));
            for (JsonNode entry : ComponentParsers.array(value.get("effects"))) {
                FireworkEffect effect = ComponentParsers.fireworkEffect(entry, ctx);
                if (effect != null) {
                    builder.addEffect(effect);
                }
            }
            return builder.build();
        });

        registry.register(DataComponentTypes.FIREWORK_EXPLOSION,
                (value, item, ctx) -> ComponentParsers.fireworkEffect(value, ctx));

        registry.register(DataComponentTypes.WRITTEN_BOOK_CONTENT, (value, item, ctx) -> {
            String title = ComponentUtils.text(value, "title");
            if (title == null) {
                ctx.logger().warning("'written-book' needs a 'title'.");
                return null;
            }
            var builder = ComponentBuilder.writtenBookContent(title, ComponentUtils.text(value, "author"));
            builder.generation(ComponentUtils.intValue(value, "generation", 0));
            builder.resolved(ComponentUtils.bool(value, "resolved", true));
            for (String page : ComponentUtils.strings(value.get("pages"))) {
                builder.addPage(ComponentBuilder.text(page));
            }
            return builder.build();
        });

        registry.register(DataComponentTypes.WRITABLE_BOOK_CONTENT, (value, item, ctx) -> {
            var builder = ComponentBuilder.writableBookContent();
            for (String page : ComponentUtils.strings(value.get("pages"))) {
                builder.addPage(page);
            }
            return builder.build();
        });

        registry.register(DataComponentTypes.RECIPES, (value, item, ctx) -> {
            List<Key> keys = new ArrayList<>();
            for (String raw : ComponentUtils.strings(value)) {
                Key key = ComponentParsers.key(raw);
                if (key != null) {
                    keys.add(key);
                }
            }
            return keys;
        });

        registry.register(DataComponentTypes.CAN_PLACE_ON, (value, item, ctx) -> adventurePredicate(value, ctx));
        registry.register(DataComponentTypes.CAN_BREAK, (value, item, ctx) -> adventurePredicate(value, ctx));

        registry.register(DataComponentTypes.PROVIDES_BANNER_PATTERNS, (value, item, ctx) ->
                ComponentParsers.bannerPatternTag(value.asText()));
    }

    private static io.papermc.paper.datacomponent.item.ItemAdventurePredicate adventurePredicate(
            JsonNode value, ComponentContext ctx) {
        var blocks = ComponentParsers.blockSet(value, ctx);
        if (blocks == null) {
            return null;
        }
        return ComponentBuilder.itemAdventurePredicate()
                .addPredicate(ComponentBuilder.blockPredicate(blocks))
                .build();
    }

    // ---------------------------------------------------------------- 别名

    private static void registerAliases(ComponentRegistry registry) {
        registry.alias("display-name", "custom_name");
        registry.alias("glint", "enchantment_glint_override");
        registry.alias("item-model", "item_model");
        registry.alias("model", "item_model");
        registry.alias("maxdamage", "max_damage");
        registry.alias("max-damage", "max_damage");
        registry.alias("custom-model-data", "custom_model_data");
        registry.alias("model-data", "custom_model_data");
        registry.alias("tooltip", "tooltip_display");
        registry.alias("hide-tooltip", "tooltip_display");
        registry.alias("attributes", "attribute_modifiers");
        registry.alias("equippable-on-head", "equippable");
        registry.alias("enchant", "enchantments");
        registry.alias("enchantment", "enchantments");
        registry.alias("stored-enchant", "stored_enchantments");
        registry.alias("color", "dyed_color");
        registry.alias("dye", "dyed_color");
        registry.alias("armor-trim", "trim");
        registry.alias("damage-resistance", "damage_resistant");
        registry.alias("max-stack", "max_stack_size");
        registry.alias("stack-size", "max_stack_size");
        registry.alias("attribute", "attribute_modifiers");
        registry.alias("stew-effects", "suspicious_stew_effects");
        registry.alias("recipe-unlock", "recipes");
        registry.alias("written-book", "written_book_content");
        registry.alias("writable-book", "writable_book_content");
    }
}
