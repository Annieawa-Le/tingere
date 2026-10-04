package org.icarus.tingere.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.gui.EditorSession;
import org.icarus.tingere.gui.MatchMode;
import org.icarus.tingere.gui.RecipeKind;
import org.icarus.tingere.nms.ComponentSnbt;
import org.icarus.tingere.parser.RecipeParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 把编辑器里摆好的配方保存到文件。
 */
public class RecipeWriter {

    private static final String RECIPES_FOLDER = "recipes";
    private static final String DEFAULT_NAMESPACE = "default";

    private final Tingere plugin;
    private final RecipeParser parser = new RecipeParser();
    private final ObjectMapper mapper = new ObjectMapper();

    public RecipeWriter(Tingere plugin) {
        this.plugin = plugin;
    }

    public String save(EditorSession session, Inventory editor) throws IOException {
        String namespace = trimmed(session.getNamespace());
        if (namespace.isEmpty()) {
            namespace = DEFAULT_NAMESPACE;
        }
        String id = trimmed(session.getRecipeId());
        if (id.isEmpty()) {
            id = uniqueId(editor);
        }

        String file = namespace + ".yml";
        ObjectNode recipe = build(session, editor, id);

        if (session.isEditingExisting() && session.getSourceFile() != null) {
            boolean moved = !file.equals(session.getSourceFile());
            boolean renamed = session.getSourceRecipeId() != null
                    && !session.getSourceRecipeId().equals(id);
            if (moved || renamed) {
                removeFrom(session.getSourceFile(), session.getSourceRecipeId());
            }
        }

        Path target = folder().resolve(file);
        parser.write(target, mergeInto(target, id, recipe));

        session.setNamespace(namespace);
        session.setRecipeId(id);
        session.setSourceFile(file);
        session.setSourceRecipeId(id);
        return file;
    }

    private String uniqueId(Inventory editor) {
        ItemStack result = editor == null ? null : editor.getItem(EditorSession.RESULT_SLOT);
        String base = result == null || result.getType().isAir()
                ? "recipe"
                : result.getType().name().toLowerCase(Locale.ROOT);

        Set<String> taken = plugin.getRecipeLoader().getAllRecipeKeys();
        if (!taken.contains(base)) {
            return base;
        }
        for (int suffix = 2; ; suffix++) {
            String candidate = base + "_" + suffix;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }

    public boolean delete(EditorSession session) throws IOException {
        if (!session.isEditingExisting()) {
            return false;
        }
        return removeFrom(session.getSourceFile(), session.getSourceRecipeId());
    }


    private ObjectNode build(EditorSession session, Inventory editor, String id) {
        List<ItemStack> grid = new ArrayList<>();
        for (int slot : EditorSession.GRID_SLOTS) {
            grid.add(present(editor.getItem(slot)));
        }

        ObjectNode recipe = mapper.createObjectNode();
        recipe.put("type", session.getKind().id());
        recipe.put("id", id);

        // 九宫格下标 → 用的字符，special 的 source-character 要靠它回填
        Map<Integer, Character> symbols = new LinkedHashMap<>();
        if (session.getKind() == RecipeKind.SHAPED) {
            buildShaped(recipe, session, grid, symbols);
        } else {
            buildShapeless(recipe, session, grid);
        }

        recipe.set("result", ingredient(editor.getItem(EditorSession.RESULT_SLOT), null, "产物"));

        if (session.isSpecial()) {
            recipe.set("special", special(session, symbols));
        }
        return recipe;
    }

    private void buildShaped(ObjectNode recipe, EditorSession session, List<ItemStack> grid,
                             Map<Integer, Character> symbols) {
        int minRow = 3;
        int maxRow = -1;
        int minCol = 3;
        int maxCol = -1;
        for (int i = 0; i < grid.size(); i++) {
            if (grid.get(i) == null) {
                continue;
            }
            minRow = Math.min(minRow, i / 3);
            maxRow = Math.max(maxRow, i / 3);
            minCol = Math.min(minCol, i % 3);
            maxCol = Math.max(maxCol, i % 3);
        }
        if (maxRow < 0) {
            throw new IllegalArgumentException("九宫格里还没放材料");
        }

        Map<String, Character> assigned = new LinkedHashMap<>();
        ObjectNode ingredients = mapper.createObjectNode();
        ArrayNode pattern = mapper.createArrayNode();
        char next = 'A';

        for (int row = minRow; row <= maxRow; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = minCol; col <= maxCol; col++) {
                int index = row * 3 + col;
                ItemStack item = grid.get(index);
                if (item == null) {
                    line.append(' ');
                    continue;
                }
                // 材质、模式、组件都一样的格子共用一个字符，生成的 pattern 才不会写成 ABC/DEF
                String signature = item.getType().name() + '|' + session.metaOf(index).getMatchMode()
                        + '|' + ComponentSnbt.exportAll(item);
                Character symbol = assigned.get(signature);
                if (symbol == null) {
                    symbol = next++;
                    assigned.put(signature, symbol);
                    ingredients.set(String.valueOf(symbol), ingredient(item, session.metaOf(index), "第 " + (index + 1) + " 格"));
                }
                symbols.put(index, symbol);
                line.append(symbol);
            }
            pattern.add(line.toString());
        }

        recipe.set("pattern", pattern);
        recipe.set("ingredients", ingredients);
    }

    private void buildShapeless(ObjectNode recipe, EditorSession session, List<ItemStack> grid) {
        ArrayNode ingredients = mapper.createArrayNode();
        for (int i = 0; i < grid.size(); i++) {
            ItemStack item = grid.get(i);
            if (item != null) {
                ingredients.add(ingredient(item, session.metaOf(i), "第 " + (i + 1) + " 格"));
            }
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("九宫格里还没放材料");
        }
        recipe.set("ingredients", ingredients);
    }

    private ObjectNode special(EditorSession session, Map<Integer, Character> symbols) {
        String target = session.getSpecialTargetMaterial();
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("特殊配方要填产物材质（special.target-material）");
        }

        ObjectNode node = mapper.createObjectNode();
        node.put("target-material", target.trim());

        for (Map.Entry<Integer, Character> entry : symbols.entrySet()) {
            if (session.metaOf(entry.getKey()).isSpecialSource()) {
                node.put("source-character", String.valueOf(entry.getValue()));
                break;
            }
        }
        if (!session.isCopyInput()) {
            node.put("copy-input", false);
        }
        return node;
    }

    private ObjectNode ingredient(ItemStack item, EditorSession.IngredientMeta meta, String label) {
        if (item == null) {
            throw new IllegalArgumentException(label + "是空的");
        }
        ObjectNode node = mapper.createObjectNode();
        node.put("material", item.getType().name());
        if (item.getAmount() > 1) {
            node.put("amount", item.getAmount());
        }
        MatchMode mode = meta == null ? MatchMode.CONTAIN : meta.getMatchMode();
        if (mode.yamlValue() != null) {
            node.put("match-mode", mode.yamlValue());
        }
        // 仅材质那档重新加载时根本不看组件；精确与包含都得把组件写下来
        if (mode != MatchMode.MATERIAL) {
            JsonNode components = ComponentSnbt.exportJson(item);
            if (!components.isEmpty()) {
                node.set("components", components);
            }
        }
        return node;
    }

    private ObjectNode mergeInto(Path file, String id, ObjectNode recipe) throws IOException {
        if (!Files.isRegularFile(file)) {
            return recipe;
        }

        JsonNode existing = parser.read(file);
        if (existing.isObject() && existing.has("type")) {
            String existingId = existing.path("id").asText(null);
            if (id.equals(existingId)) {
                return recipe;
            }
            ObjectNode container = mapper.createObjectNode();
            if (existingId != null) {
                container.set(existingId, existing);
            }
            container.set(id, recipe);
            return container;
        }
        if (existing.isObject()) {
            ObjectNode container = (ObjectNode) existing;
            container.set(id, recipe);
            return container;
        }
        return recipe;
    }

    private boolean removeFrom(String relative, String id) throws IOException {
        if (relative == null || id == null) {
            return false;
        }
        Path file = folder().resolve(relative);
        if (!Files.isRegularFile(file)) {
            return false;
        }

        JsonNode root = parser.read(file);
        if (!root.isObject()) {
            return false;
        }

        if (root.has("type")) {
            if (!id.equals(root.path("id").asText(null))) {
                return false;
            }
            Files.deleteIfExists(file);
            return true;
        }

        ObjectNode container = (ObjectNode) root;
        if (container.remove(id) == null) {
            return false;
        }
        if (container.isEmpty()) {
            Files.deleteIfExists(file);
        } else {
            parser.write(file, container);
        }
        return true;
    }

    private Path folder() {
        return plugin.getDataFolder().toPath().resolve(RECIPES_FOLDER).toAbsolutePath().normalize();
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static ItemStack present(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item;
    }
}
