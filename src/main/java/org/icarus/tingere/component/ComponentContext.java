package org.icarus.tingere.component;

import org.icarus.tingere.Tingere;

import java.util.logging.Logger;

/**
 * 应用组件时的共享上下文。
 *
 * @param plugin    插件实例，用于命名空间与日志
 * @param keyPrefix 生成 {@link org.bukkit.NamespacedKey} 时使用的前缀，
 *                  保证同一物品上多次应用不会互相覆盖
 */
public record ComponentContext(Tingere plugin, String keyPrefix) {

    public Logger logger() {
        return plugin.getLogger();
    }
}
