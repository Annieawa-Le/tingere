package org.icarus.tingere.component;

import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.nms.NbtComponentSupport;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 通用数据组件应用器，也是组件系统对外的门面。
 * <p>
 * 它为每个插件实例缓存一份 {@link ComponentRegistry}（注册表初始化需要反射扫描
 * {@code DataComponentTypes}，只做一次即可），因此可以像静态工具那样调用，
 * 而不需要在每个调用点持有 registry。
 * <p>
 * 绝大多数组件是就地写进传入的物品的，但配置里一旦出现 {@code entity-data}，
 * 返回值就会是一个新的 ItemStack——NBT 只能走 NMS 复制往返，没法就地改。
 * 所以<b>调用方一律要接住返回值</b>，别依赖副作用。
 */
public final class ComponentApplyer {

    private static final Map<Tingere, ComponentApplyer> INSTANCES = new WeakHashMap<>();

    private final Tingere plugin;
    private final ComponentRegistry registry;

    public ComponentApplyer(Tingere plugin) {
        this.plugin = plugin;
        this.registry = new ComponentRegistry(plugin);
    }

    public static ComponentApplyer of(Tingere plugin) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(plugin, ComponentApplyer::new);
        }
    }

    /** 便捷入口：把 {@code components} 应用到 {@code item}，返回应用后的物品（可能是新对象）。 */
    public static ItemStack apply(Tingere plugin, ItemStack item, JsonNode components, String keyPrefix) {
        return of(plugin).apply(item, components, keyPrefix);
    }

    public ItemStack apply(ItemStack item, JsonNode components, String keyPrefix) {
        if (item == null || components == null || !components.isObject()) {
            return item;
        }
        registry.apply(item, components, new ComponentContext(plugin, keyPrefix));
        return NbtComponentSupport.applyEntityData(item, entityData(components), plugin.getLogger());
    }

    public ComponentRegistry registry() {
        return registry;
    }

    /** {@code entity-data} 不在 paper-api 的组件表里：注册表放过不处理，真正写入在 NMS 通道。 */
    private static JsonNode entityData(JsonNode components) {
        JsonNode value = components.get("entity_data");
        return value == null ? components.get("entity-data") : value;
    }
}

