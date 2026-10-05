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
import org.icarus.tingere.parser.ParseContext;
import org.icarus.tingere.parser.ParseResult;

import java.util.Iterator;
import java.util.Map;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class ComponentNbt {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ComponentNbt() {
    }

    public static DataComponentPatch decode(ObjectNode components, String label) throws IllegalArgumentException {
        var result = DataComponentPatch.CODEC.parse(jsonOps(), gson(components));
        return result.result().orElseThrow(() -> new IllegalArgumentException(
                "invalid data component '" + label + "': " + result.error().orElseThrow().message()));
    }


    public static DataComponentExactPredicate exactPredicate(DataComponentPatch patch) {
        DataComponentMap.Builder map = DataComponentMap.builder();
        patch.entrySet().forEach(entry -> entry.getValue().ifPresent(value ->
                map.set((net.minecraft.core.component.DataComponentType) entry.getKey(), value)));
        return DataComponentExactPredicate.allOf(map.build());
    }

    /**
     * 编码失败返回空 Object
     */
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
     * 逐条解析
     */
    public static ParseResult<DataComponentPatch> decodeLenient(ObjectNode components, ParseContext ctx) {
        DataComponentPatch.Builder builder = DataComponentPatch.builder();
        for (Iterator<Map.Entry<String, JsonNode>> fields = components.fields(); fields.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = fields.next();
            ObjectNode single = JsonNodeFactory.instance.objectNode();
            single.set(entry.getKey(), entry.getValue());
            try {
                decode(single, entry.getKey()).entrySet().forEach(e -> copyEntry(builder, e));
            } catch (IllegalArgumentException e) {
                ctx.componentError(entry.getKey(), reason(e));
            }
        }
        return ctx.ok(builder.build());
    }

    private static String reason(IllegalArgumentException e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        int colon = message.indexOf(": ");
        return colon < 0 ? message : message.substring(colon + 2);
    }

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



