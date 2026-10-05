package org.icarus.tingere.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.core.component.DataComponentPatch;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.nms.ComponentNbt;
import org.icarus.tingere.nms.NbtComponentSupport;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 解码全部交给原版：{@link ComponentDialect}
 * 说的有道理，既然是数据包为什么不复用呢。
 */
public final class ComponentApplier {

    private static final Map<Tingere, ComponentApplier> INSTANCES = new HashMap<>();
    private static final String CUSTOM_NAME = "custom_name";
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

    public static ItemStack apply(Tingere plugin, ItemStack item, JsonNode components) {
        return of(plugin).apply(item, components);
    }

    public ItemStack apply(ItemStack item, JsonNode components) {
        if (item == null || item.getType().isAir() || components == null || !components.isObject()) {
            return item;
        }

        ObjectNode vanilla = ComponentDialect.toVanilla(components);
        applyAffixes(item, vanilla);
        vanilla.remove(PREFIX);
        vanilla.remove(SUFFIX);

        net.minecraft.world.item.ItemStack handle = CraftItemStack.asNMSCopy(item);
        if (handle == null) {
            logger.warning("Cannot apply data components to an item that is not a CraftItemStack.");
            return item;
        }

        if (!vanilla.isEmpty()) {
            try {
                DataComponentPatch patch = ComponentNbt.decodeLenient(vanilla);
                handle.applyComponents(patch);
            } catch (RuntimeException e) {
                logger.warning("Failed to apply data components: " + e.getMessage());
            }
        }
        NbtComponentSupport.applyEntityData(handle, entityData(components), logger);
        return CraftItemStack.asBukkitCopy(handle);
    }


    private void applyAffixes(ItemStack item, ObjectNode vanilla) {
        if (vanilla.has(CUSTOM_NAME)) {
            return;
        }
        JsonNode prefix = vanilla.get(PREFIX);
        JsonNode suffix = vanilla.get(SUFFIX);
        if (prefix == null && suffix == null) {
            return;
        }

        net.minecraft.world.item.ItemStack handle = CraftItemStack.asNMSCopy(item);
        if (handle == null) {
            return;
        }
        Component name =
                io.papermc.paper.adventure.PaperAdventure.asAdventure(handle.getHoverName());
        if (prefix != null) {
            name = literal(prefix).append(name);
        }
        if (suffix != null) {
            name = name.append(literal(suffix));
        }
        vanilla.set(CUSTOM_NAME, ComponentDialect.textNode(JsonNodeFactory.instance.textNode(
                GsonComponentSerializer.gson().serialize(name))));
    }

    private static Component literal(JsonNode node) {
        return MiniMessage.miniMessage()
                .deserialize(node == null || node.isNull() ? "" : node.asText());
    }


    private static JsonNode entityData(JsonNode components) {
        JsonNode value = components.get("entity_data");
        return value == null ? components.get("entity-data") : value;
    }
}

