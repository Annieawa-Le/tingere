package org.icarus.tingere.component;

import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 把一个配置值解码成某个数据组件的值对象。
 * <p>
 * 泛型 {@code T} 在注册点——也就是调用
 * {@link ComponentRegistry#register(io.papermc.paper.datacomponent.DataComponentType.Valued, ComponentCodec)}
 * 的那一行——由编译器固定下来，于是解码结果与组件类型的类型参数天然一致。
 * 这就是整套系统不需要反射猜测 {@code Class}、也不需要强制转换的原因。
 *
 * @param <T> 组件值类型
 */
@FunctionalInterface
public interface ComponentCodec<T> {

    /**
     * @param value   配置中的原始值
     * @param item    正在被修改的物品（部分组件需要读取现有值再合并，例如附魔）
     * @param context 共享上下文
     * @return 解码出的组件值；返回 {@code null} 表示该组件不应用
     */
    @Nullable
    T decode(JsonNode value, ItemStack item, ComponentContext context);
}
