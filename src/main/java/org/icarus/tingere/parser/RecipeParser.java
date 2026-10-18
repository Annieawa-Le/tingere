package org.icarus.tingere.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.bukkit.Material;
import org.icarus.tingere.recipe.CookingRecipeDefinition;
import org.icarus.tingere.recipe.RecipeDefinition;
import org.icarus.tingere.recipe.ShapedRecipeDefinition;
import org.icarus.tingere.recipe.ShapelessRecipeDefinition;
import org.icarus.tingere.recipe.SmithingRecipeDefinition;
import org.icarus.tingere.recipe.StonecuttingRecipeDefinition;
import org.icarus.tingere.recipe.TransmuteRecipeDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RecipeParser {

    private final ObjectMapper mapper;

    public RecipeParser() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Material.class, new MaterialDeserializer());

        this.mapper = YAMLMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .addModule(module)
                .build();
    }

    public JsonNode read(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            JsonNode root = mapper.readTree(input);
            return root == null ? mapper.getNodeFactory().missingNode() : root;
        }
    }

    public void write(Path file, JsonNode root) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".tingere-recipe-", ".tmp");
        try {
            try {
                try (OutputStream output = Files.newOutputStream(temp)) {
                    mapper.writeValue(output, root);
                }
            } catch (LinkageError error) {
                // 部分老牧师产物的 lib 是不完全的，需要 fallback
                writeWithServerYaml(temp, root);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void writeWithServerYaml(Path file, JsonNode root) throws IOException {
        try {
            Class<?> yamlType = Class.forName("org.yaml.snakeyaml.Yaml");
            Object yaml = yamlType.getConstructor().newInstance();
            Object text = yamlType.getMethod("dump", Object.class).invoke(yaml, toJava(root));
            Files.writeString(file, String.valueOf(text), StandardCharsets.UTF_8);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to write recipe file with the server YAML implementation", e);
        }
    }

    private static Object toJava(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> map.put(entry.getKey(), toJava(entry.getValue())));
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(entry -> list.add(toJava(entry)));
            return list;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.canConvertToInt() ? (Object) node.asInt() : (Object) node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        return node.asText();
    }

    public RecipeDefinition bind(JsonNode node) throws IOException {
        if (!node.isObject()) {
            throw new IllegalArgumentException("a recipe must be a mapping, got " + node.getNodeType());
        }

        JsonNode typeNode = node.get("type");
        if (typeNode == null || typeNode.isNull()) {
            throw new IllegalArgumentException("missing required field 'type'");
        }

        String type = typeNode.asText().toLowerCase(Locale.ROOT);
        ObjectNode payload = node.deepCopy();
        payload.remove("type");
        migrateLegacyKey(payload);

        return switch (type) {
            case "shaped" -> mapper.treeToValue(payload, ShapedRecipeDefinition.class);
            case "shapeless" -> mapper.treeToValue(payload, ShapelessRecipeDefinition.class);
            case "transmute" -> mapper.treeToValue(payload, TransmuteRecipeDefinition.class);
            case "furnace", "blast", "smoker" -> {
                payload.put("kind", CookingRecipeDefinition.Kind.of(type).name());
                yield mapper.treeToValue(payload, CookingRecipeDefinition.class);
            }
            case "stonecutter" -> mapper.treeToValue(payload, StonecuttingRecipeDefinition.class);
            case "smithing" -> mapper.treeToValue(payload, SmithingRecipeDefinition.class);
            default -> throw new IllegalArgumentException("unknown recipe type '" + type
                    + "' (expected shaped, shapeless, transmute, furnace, blast, smoker, stonecutter or smithing)");
        };
    }

    private static void migrateLegacyKey(ObjectNode payload) {
        if (!payload.hasNonNull("key")) {
            return;
        }
        if (payload.hasNonNull("id")) {
            throw new IllegalArgumentException("both 'id' and the deprecated 'key' are set; Should keep 'id' only");
        }
        payload.set("id", payload.remove("key"));
    }

    public static String labelOf(JsonNode node, String fallback) {
        JsonNode id = node.hasNonNull("id") ? node.get("id") : node.get("key");
        return id == null || id.isNull() ? fallback : id.asText();
    }
}
