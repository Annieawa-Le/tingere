package org.icarus.tingere.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import org.icarus.tingere.component.ComponentApplier;

/**
 * 转写 / 特殊配方在合成瞬间要补到结果上的缓存件
 */
public record ResultOverride(ComponentApplier.Parsed parsed, JsonNode components, int amount) {
}
