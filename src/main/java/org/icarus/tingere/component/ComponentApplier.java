package org.icarus.tingere.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.nms.ComponentNbt;
import org.icarus.tingere.nms.NbtComponentSupport;
import org.icarus.tingere.parser.ParseContext;
import org.icarus.tingere.parser.ParseResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * 说的有道理，既然是数据包为什么不复用呢。
 * - Annie
 * 严格而言不算是数据包而是走了原版方法。
 * 不做数据包是因为我只需要这点东西就可以了，不需要那么多花里胡哨的。
 * 那说回来了，为什么我不 mixin 写这个呢。
 * - P
 */
public final class ComponentApplier {

    private static final Map<Tingere, ComponentApplier> INSTANCES = new HashMap<>();
    private static final String PREFIX = "prefix";
    private static final String SUFFIX = "suffix";

    private final Logger logger;

    public ComponentApplier(Tingere plugin) {
        this.logger = plugin.getLogger();
    }

    public static ComponentApplier of(Tingere plugin) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(plugin, ComponentApplier::new);
        }
    }

    public record Parsed(DataComponentPatch patch, JsonNode prefix, JsonNode suffix) {
        public static final Parsed EMPTY = new Parsed(DataComponentPatch.EMPTY, null, null);
    }

    public static ParseResult<Parsed> parse(JsonNode components, ParseContext ctx) {
        if (components == null || !components.isObject()) {
            return ctx.ok(Parsed.EMPTY);
        }
        ObjectNode vanilla = ComponentDialect.toVanilla(components);
        JsonNode prefix = vanilla.remove(PREFIX);
        JsonNode suffix = vanilla.remove(SUFFIX);
        if (vanilla.isEmpty()) {
            return ctx.ok(new Parsed(DataComponentPatch.EMPTY, prefix, suffix));
        }
        return ComponentNbt.decodeLenient(vanilla, ctx)
                .map(patch -> new Parsed(patch, prefix, suffix));
    }

    public static ItemStack apply(Tingere plugin, ItemStack item, JsonNode components) {
        return of(plugin).apply(item, parse(components, new ParseContext(null, null)).orElseThrow(), components);
    }

    public ItemStack apply(ItemStack item, Parsed parsed, JsonNode components) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        net.minecraft.world.item.ItemStack handle = CraftItemStack.asNMSCopy(item);
        if (handle == null) {
            logger.warning("Cannot apply data components to an item that is not a CraftItemStack.");
            return item;
        }

        if (!parsed.patch().isEmpty()) {
            handle.applyComponents(parsed.patch());
        }
        applyAffixes(handle, parsed);
        NbtComponentSupport.applyEntityData(handle, entityData(components));
        return CraftItemStack.asBukkitCopy(handle);
    }


    private void applyAffixes(net.minecraft.world.item.ItemStack handle, Parsed parsed) {
        if (Objects.requireNonNull(parsed.patch().get(DataComponents.CUSTOM_NAME)).isPresent()) return;
        JsonNode prefix = parsed.prefix();
        JsonNode suffix = parsed.suffix();
        if (prefix == null && suffix == null) {
            return;
        }

        Component name = PaperAdventure.asAdventure(handle.getHoverName());
        if (prefix != null) {
            name = literal(prefix).append(name);
        }
        if (suffix != null) {
            name = name.append(literal(suffix));
        }
        handle.set(DataComponents.CUSTOM_NAME, PaperAdventure.asVanilla(name));
    }

    private static Component literal(JsonNode node) {
        return MiniMessage.miniMessage()
                .deserialize(node == null || node.isNull() ? "" : node.asText());
    }


    private static JsonNode entityData(JsonNode components) {
        if (components == null || !components.isObject()) return null;

        JsonNode value = components.get("entity_data");
        return value == null ? components.get("entity-data") : value;
    }
}

