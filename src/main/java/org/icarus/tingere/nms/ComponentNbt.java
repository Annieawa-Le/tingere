package org.icarus.tingere.nms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.RegistryOps;
import org.icarus.tingere.component.ComponentDialect;

import java.util.Iterator;
import java.util.Map;

/**
 * 数据组件编解码通道（JSON ↔ 原版 {@link DataComponentPatch#CODEC}）。
 * <p>
 * 组件的结构、字段名与取值范围全部交给原版 codec，这里只做"节点形态"的搬运：
 * {@link com.fasterxml.jackson.databind.JsonNode} 与 Gson 的 {@link JsonElement} 之间转一趟，
 * 再套上带注册表的 ops。键名 / 别名 / MiniMessage 的翻译在 {@link ComponentDialect} 里完成。
 * <p>
 * 唯二不受 codec 管辖的输入也在这里消化：{@code entity-data} 走 {@link NbtComponentSupport}，
 * {@code prefix} / {@code suffix} 需要读物品当前的名字，由 {@link ComponentApplyer} 处理。
 */
public final class ComponentNbt {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ComponentNbt() {
    }

    /**
     * JSON 节点 → 组件补丁。
     * <p>
     * 单个组件解不出来时不静默吞掉：把原版 codec 的报错原样带出去，调用方才能按组件名
     * 记一条可操作的警告（是值写错了，而不是"这个组件不存在"）。
     */
    public static DataComponentPatch decode(ObjectNode components, String label) {
        var result = DataComponentPatch.CODEC.parse(jsonOps(), gson(components));
        return result.result().orElseThrow(() -> new IllegalArgumentException(
                "invalid data component '" + label + "': " + result.error().orElseThrow().message()));
    }

    /**
     * 组件节点 → 匹配谓词。
     * <p>
     * 原版的 {@code DataComponentExactPredicate} 正是 {@code contain} 想要的语义：只检查自己
     * 列出的那几项，物品上多出来的组件一律不管。它的结构跟补丁同构，所以直接在补丁上收一层
     * {@code DataComponentMap} 就能拿到。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static DataComponentExactPredicate exactPredicate(ObjectNode components) {
        DataComponentPatch patch = decodeLenient(components);
        DataComponentMap.Builder map = DataComponentMap.builder();
        patch.entrySet().forEach(entry -> entry.getValue().ifPresent(value ->
                map.set((net.minecraft.core.component.DataComponentType) entry.getKey(), value)));
        return DataComponentExactPredicate.allOf(map.build());
    }

    /** 组件补丁 → JSON 节点；编码失败返回空物件。 */
    public static ObjectNode encode(DataComponentPatch patch) {
        JsonElement encoded = DataComponentPatch.CODEC
                .encodeStart(jsonOps(), patch)
                .result()
                .orElse(null);
        if (encoded == null || !encoded.isJsonObject()) {
            return JsonNodeFactory.instance.objectNode();
        }
        return toJackson(encoded);
    }

    /**
     * 把 JSON 里的组件节点逐条解析。
     * <p>
     * 原版 codec 是"一票否决"的：只要有一条不认识，整张 map 都解不出来。为了让配置里
     * 拼错一个组件不至于连累其余部分，这里把每条单独喂给一次 codec，认识这条收下、
     * 不认识的记一条警告跳过。代价是 n 次解析，收益是错误信息能精确到具体组件。
     */
    public static DataComponentPatch decodeLenient(ObjectNode components) {
        DataComponentPatch.Builder builder = DataComponentPatch.builder();
        for (Iterator<Map.Entry<String, JsonNode>> fields = components.fields(); fields.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = fields.next();
            ObjectNode single = JsonNodeFactory.instance.objectNode();
            single.set(entry.getKey(), entry.getValue());
            decode(single, entry.getKey()).entrySet().forEach(e -> copyEntry(builder, e));
        }
        return builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void copyEntry(DataComponentPatch.Builder builder,
                                  Map.Entry<net.minecraft.core.component.DataComponentType<?>, java.util.Optional<?>> entry) {
        if (entry.getValue().isPresent()) {
            builder.set((net.minecraft.core.component.DataComponentType) entry.getKey(), entry.getValue().get());
        } else {
            builder.remove((net.minecraft.core.component.DataComponentType) entry.getKey());
        }
    }

    // ------------------------------------------------------------------ 转换

    private static JsonElement gson(JsonNode node) {
        try {
            return com.google.gson.JsonParser.parseString(MAPPER.writeValueAsString(node));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to convert component data to JSON: " + e.getMessage(), e);
        }
    }

    private static ObjectNode toJackson(JsonElement element) {
        try {
            return (ObjectNode) MAPPER.readTree(element.toString());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to convert component data back to JSON: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ 注册表

    /**
     * 组件编码必须跑在带注册表的 ops 上：附魔、属性这些是数据包注册表里的条目，
     * 裸 {@code JsonOps} 查不到它们，编码会直接抛 "Can't access registry"。
     */
    private static RegistryOps<JsonElement> jsonOps() {
        return registryOps(JsonOps.INSTANCE);
    }

    private static <T> RegistryOps<T> registryOps(DynamicOps<T> ops) {
        return RegistryOps.create(ops, registryAccess());
    }

    private static HolderLookup.Provider registryAccess() {
        return MinecraftServer.getServer().registryAccess();
    }
}



