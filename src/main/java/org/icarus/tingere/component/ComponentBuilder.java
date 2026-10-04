package org.icarus.tingere.component;

import io.papermc.paper.block.BlockPredicate;
import io.papermc.paper.datacomponent.item.AttackRange;
import io.papermc.paper.datacomponent.item.BannerPatternLayers;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ChargedProjectiles;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.DamageResistant;
import io.papermc.paper.datacomponent.item.DeathProtection;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.Enchantable;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.Fireworks;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.ItemAdventurePredicate;
import io.papermc.paper.datacomponent.item.ItemArmorTrim;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.JukeboxPlayable;
import io.papermc.paper.datacomponent.item.KineticWeapon;
import io.papermc.paper.datacomponent.item.LodestoneTracker;
import io.papermc.paper.datacomponent.item.MapDecorations;
import io.papermc.paper.datacomponent.item.MapId;
import io.papermc.paper.datacomponent.item.MapItemColor;
import io.papermc.paper.datacomponent.item.OminousBottleAmplifier;
import io.papermc.paper.datacomponent.item.PiercingWeapon;
import io.papermc.paper.datacomponent.item.PotDecorations;
import io.papermc.paper.datacomponent.item.PotionContents;
import io.papermc.paper.datacomponent.item.Repairable;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.SeededContainerLoot;
import io.papermc.paper.datacomponent.item.SuspiciousStewEffects;
import io.papermc.paper.datacomponent.item.SwingAnimation;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.datacomponent.item.UseCooldown;
import io.papermc.paper.datacomponent.item.UseEffects;
import io.papermc.paper.datacomponent.item.UseRemainder;
import io.papermc.paper.datacomponent.item.Weapon;
import io.papermc.paper.datacomponent.item.WritableBookContent;
import io.papermc.paper.datacomponent.item.WrittenBookContent;
import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import io.papermc.paper.datacomponent.item.blocksattacks.ItemDamageFunction;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.util.TriState;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.JukeboxSong;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.map.MapCursor;
import org.bukkit.potion.PotionEffect;

import java.util.List;
import java.util.Map;

/**
 * 复合组件值的构造辅助。
 */

@SuppressWarnings("UnstableApiUsage")
public final class ComponentBuilder {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private ComponentBuilder() {
    }

    // ---------------------------------------------------------------- 文本

    /** MiniMessage 文本 → Adventure 文本组件。 */
    public static Component text(String raw) {
        return MINI_MESSAGE.deserialize(raw);
    }

    public static ItemLore lore(List<Component> lines) {
        return ItemLore.lore(lines);
    }

    public static ItemEnchantments enchantments(Map<Enchantment, Integer> enchantments) {
        return ItemEnchantments.itemEnchantments(enchantments);
    }

    public static ItemAttributeModifiers.Builder attributes() {
        return ItemAttributeModifiers.itemAttributes();
    }

    public static CustomModelData.Builder customModelData() {
        return CustomModelData.customModelData();
    }

    public static TooltipDisplay.Builder tooltipDisplay() {
        return TooltipDisplay.tooltipDisplay();
    }

    public static Equippable.Builder equippable(EquipmentSlot slot) {
        return Equippable.equippable(slot);
    }

    public static UseEffects useEffects(boolean canSprint, boolean interactVibrations, float speedMultiplier) {
        return UseEffects.useEffects()
                .canSprint(canSprint)
                .interactVibrations(interactVibrations)
                .speedMultiplier(speedMultiplier)
                .build();
    }

    // ------------------------------------------------------------ 食物与使用

    public static FoodProperties food(int nutrition, float saturation, boolean canAlwaysEat) {
        return FoodProperties.food()
                .nutrition(nutrition)
                .saturation(saturation)
                .canAlwaysEat(canAlwaysEat)
                .build();
    }

    public static Consumable.Builder consumable() {
        return Consumable.consumable();
    }

    public static ConsumeEffect applyStatusEffects(List<PotionEffect> effects, float probability) {
        return ConsumeEffect.applyStatusEffects(effects, probability);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ConsumeEffect removeStatusEffects(RegistryKeySet<?> effects) {
        return ConsumeEffect.removeEffects((RegistryKeySet) effects);
    }

    public static ConsumeEffect clearAllStatusEffects() {
        return ConsumeEffect.clearAllStatusEffects();
    }

    public static ConsumeEffect teleportRandomly(float diameter) {
        return ConsumeEffect.teleportRandomlyEffect(diameter);
    }

    public static ConsumeEffect playSound(Key sound) {
        return ConsumeEffect.playSoundConsumeEffect(sound);
    }

    public static DeathProtection.Builder deathProtection() {
        return DeathProtection.deathProtection();
    }

    public static PotionContents.Builder potionContents() {
        return PotionContents.potionContents();
    }

    public static UseCooldown.Builder useCooldown(float seconds) {
        return UseCooldown.useCooldown(seconds);
    }

    public static UseRemainder useRemainder(ItemStack item) {
        return UseRemainder.useRemainder(item);
    }

    public static SuspiciousStewEffects.Builder suspiciousStewEffects() {
        return SuspiciousStewEffects.suspiciousStewEffects();
    }

    // ---------------------------------------------------------------- 战斗

    public static Tool.Builder tool() {
        return Tool.tool();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Tool.Rule toolRule(RegistryKeySet<?> blocks, Float speed, TriState correctForDrops) {
        return Tool.rule((RegistryKeySet) blocks, speed, correctForDrops);
    }

    public static Weapon.Builder weapon() {
        return Weapon.weapon();
    }

    public static AttackRange.Builder attackRange() {
        return AttackRange.attackRange();
    }

    public static BlocksAttacks.Builder blocksAttacks() {
        return BlocksAttacks.blocksAttacks();
    }

    public static DamageReduction.Builder damageReduction() {
        return DamageReduction.damageReduction();
    }

    public static ItemDamageFunction.Builder itemDamageFunction() {
        return ItemDamageFunction.itemDamageFunction();
    }

    public static PiercingWeapon.Builder piercingWeapon() {
        return PiercingWeapon.piercingWeapon();
    }

    public static KineticWeapon.Builder kineticWeapon() {
        return KineticWeapon.kineticWeapon();
    }

    public static KineticWeapon.Condition kineticCondition(int maxDurationTicks, float minSpeed, float minRelativeSpeed) {
        return KineticWeapon.condition(maxDurationTicks, minSpeed, minRelativeSpeed);
    }

    public static Enchantable enchantable(int value) {
        return Enchantable.enchantable(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Repairable repairable(RegistryKeySet<?> types) {
        return Repairable.repairable((RegistryKeySet) types);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static DamageResistant damageResistant(TagKey<?> types) {
        return DamageResistant.damageResistant((TagKey) types);
    }

    // ------------------------------------------------------------ 装备与外观

    public static DyedItemColor dyedColor(Color color) {
        return DyedItemColor.dyedItemColor(color);
    }

    public static ItemArmorTrim armorTrim(TrimMaterial material, TrimPattern pattern) {
        return ItemArmorTrim.itemArmorTrim(new ArmorTrim(material, pattern)).build();
    }

    public static BannerPatternLayers.Builder bannerPatternLayers() {
        return BannerPatternLayers.bannerPatternLayers();
    }

    public static Pattern bannerPattern(DyeColor color, PatternType type) {
        return new Pattern(color, type);
    }

    public static PotDecorations.Builder potDecorations() {
        return PotDecorations.potDecorations();
    }

    public static MapItemColor.Builder mapItemColor() {
        return MapItemColor.mapItemColor();
    }

    public static MapId mapId(int id) {
        return MapId.mapId(id);
    }

    public static MapDecorations.Builder mapDecorations() {
        return MapDecorations.mapDecorations();
    }

    public static MapDecorations.DecorationEntry mapDecoration(MapCursor.Type type, double x, double z, float rotation) {
        return MapDecorations.decorationEntry(type, x, z, rotation);
    }

    // ---------------------------------------------------------------- 容器

    public static ItemContainerContents containerContents(List<ItemStack> items) {
        return ItemContainerContents.containerContents(items);
    }

    public static BundleContents bundleContents(List<ItemStack> items) {
        return BundleContents.bundleContents(items);
    }

    public static ChargedProjectiles chargedProjectiles(List<ItemStack> items) {
        return ChargedProjectiles.chargedProjectiles(items);
    }

    public static SeededContainerLoot.Builder seededContainerLoot(Key lootTable) {
        return SeededContainerLoot.seededContainerLoot(lootTable);
    }

    public static LodestoneTracker.Builder lodestoneTracker() {
        return LodestoneTracker.lodestoneTracker();
    }

    // ---------------------------------------------------------------- 其它

    public static ResolvableProfile.Builder resolvableProfile() {
        return ResolvableProfile.resolvableProfile();
    }

    public static Fireworks.Builder fireworks() {
        return Fireworks.fireworks();
    }

    public static FireworkEffect.Builder fireworkEffect() {
        return FireworkEffect.builder();
    }

    public static WrittenBookContent.Builder writtenBookContent(String title, String author) {
        return WrittenBookContent.writtenBookContent(title, author);
    }

    public static WritableBookContent.Builder writableBookContent() {
        return WritableBookContent.writeableBookContent();
    }

    public static JukeboxPlayable.Builder jukeboxPlayable(JukeboxSong song) {
        return JukeboxPlayable.jukeboxPlayable(song);
    }

    public static OminousBottleAmplifier ominousBottleAmplifier(int amplifier) {
        return OminousBottleAmplifier.amplifier(amplifier);
    }

    public static SwingAnimation.Builder swingAnimation() {
        return SwingAnimation.swingAnimation();
    }

    public static ItemAdventurePredicate.Builder itemAdventurePredicate() {
        return ItemAdventurePredicate.itemAdventurePredicate();
    }

    public static BlockPredicate.Builder blockPredicate() {
        return BlockPredicate.predicate();
    }

    /** 一次构造出带方块集合的谓词（can-place-on / can-break 用）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockPredicate blockPredicate(RegistryKeySet<?> blocks) {
        return BlockPredicate.predicate().blocks((RegistryKeySet) blocks).build();
    }

    /** 一条盾牌伤害减免规则。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static DamageReduction damageReduction(
            RegistryKeySet<?> types, float base, float factor, float horizontalBlockingAngle) {
        DamageReduction.Builder builder = DamageReduction.damageReduction();
        if (types != null) {
            builder.type((RegistryKeySet) types);
        }
        return builder.base(base).factor(factor).horizontalBlockingAngle(horizontalBlockingAngle).build();
    }
}





