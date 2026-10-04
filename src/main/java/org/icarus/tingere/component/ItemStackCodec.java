package org.icarus.tingere.component;

import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 嵌套物品的解析。
 * <p>
 * container、bundle-contents、charged-projectiles、use-remainder 这些组件的值本身
 * 就是物品，所以需要一层"配置 → ItemStack"的转换。写法刻意与配方里的 ingredient
 * 保持一致（{@code material} / {@code amount} / {@code components}），避免出现第二套方言：
 *
 * <pre>
 * use-remainder: BOWL
 * container:
 *   - material: DIAMOND
 *     amount: 3
 *     components:
 *       custom-name: "&lt;gold&gt;闪亮钻石"
 * </pre>
 */

public final class ItemStackCodec {

    private ItemStackCodec() {
    }

    /** 解析单个物品；写法非法时记录警告并返回 null。 */
    public static ItemStack parse(JsonNode value, ComponentContext context) {
        if (value == null || value.isNull()) {
            return null;
        }

        Material material;
        int amount = 1;
        JsonNode components = null;

        if (value.isObject()) {
            material = material(value.get("material"));
            if (value.hasNonNull("amount")) {
                amount = value.get("amount").asInt(1);
            }
            components = value.get("components");
        } else {
            String raw = value.asText("").trim();
            int space = raw.indexOf(' ');
            if (space > 0) {
                material = Material.matchMaterial(raw.substring(0, space).trim());
                try {
                    amount = Integer.parseInt(raw.substring(space + 1).trim());
                } catch (NumberFormatException e) {
                    context.logger().warning("Ignored malformed item amount: " + raw);
                    return null;
                }
            } else {
                material = Material.matchMaterial(raw);
            }
        }

        if (material == null) {
            context.logger().warning("Ignored nested item with unknown material: " + value);
            return null;
        }
        if (material.isAir()) {
            return null;
        }

        ItemStack item = new ItemStack(material, Math.max(1, amount));
        if (components != null && components.isObject()) {
            item = ComponentApplyer.of(context.plugin()).apply(item, components, context.keyPrefix());
        }
        return item;
    }

    /** 解析物品列表（单个数目也可写成一项）。 */
    public static List<ItemStack> parseAll(JsonNode value, ComponentContext context) {
        List<ItemStack> items = new ArrayList<>();
        if (value == null || value.isNull()) {
            return items;
        }
        if (value.isArray()) {
            for (JsonNode entry : value) {
                ItemStack item = parse(entry, context);
                if (item != null) {
                    items.add(item);
                }
            }
            return items;
        }
        ItemStack single = parse(value, context);
        if (single != null) {
            items.add(single);
        }
        return items;
    }

    private static Material material(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return Material.matchMaterial(node.asText("").trim());
    }
}
