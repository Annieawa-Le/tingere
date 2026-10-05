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
import org.icarus.tingere.Tingere;
import org.icarus.tingere.component.ComponentDialect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

/**
 * 数据组件编解码通道（JSON ↔ 原版 {@link DataComponentPatch#CODEC}）。
 * <p>
 * 组件的结构、字段名与取值范围全部交给原版 codec，这里只做"节点形态"的搬运：
 */
public final class ComponentNbt {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ComponentNbt() {
    }

    public static DataComponentPatch decode(ObjectNode components, String label) throws IllegalArgumentException{
        var result = DataComponentPatch.CODEC.parse(jsonOps(), gson(components));
        return result.result().orElseThrow(() -> new IllegalArgumentException(
                "invalid data component '" + label + "': " + result.error().orElseThrow().message()));
    }

    /**
     * 组件节点 → 匹配谓词。
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
     */
    public static DataComponentPatch decodeLenient(ObjectNode components) throws IllegalArgumentException{
        DataComponentPatch.Builder builder = DataComponentPatch.builder();
        ArrayList<String> errors = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> fields = components.fields(); fields.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = fields.next();
            ObjectNode single = JsonNodeFactory.instance.objectNode();
            single.set(entry.getKey(), entry.getValue());
            try {
                decode(single, entry.getKey()).entrySet().forEach(e -> copyEntry(builder, e));
            }catch (IllegalArgumentException e){
                errors.add(e.getMessage());
            }
        }
        if(!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n",errors));
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



