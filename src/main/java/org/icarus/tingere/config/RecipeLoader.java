package org.icarus.tingere.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.nms.RecipeTransaction;
import org.icarus.tingere.parser.ParseContext;
import org.icarus.tingere.parser.ParseProblem;
import org.icarus.tingere.parser.ParseResult;
import org.icarus.tingere.parser.RecipeParser;
import org.icarus.tingere.recipe.Ingredient;
import org.icarus.tingere.recipe.RecipeDefinition;
import org.icarus.tingere.recipe.ResolvedRecipe;
import org.icarus.tingere.recipe.ResultOverride;
import org.icarus.tingere.recipe.ShapedRecipeDefinition;
import org.icarus.tingere.recipe.SpecialDefinition;
import org.icarus.tingere.recipe.TransmuteRecipeDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecipeLoader {

    private static final String RECIPES_FOLDER = "recipes";

    private final Tingere plugin;
    private final Logger logger;
    private final RecipeParser parser = new RecipeParser();

    private final Map<String, ItemStack> recipeResults = new HashMap<>();
    private final Map<String, List<ItemStack>> recipeIngredients = new HashMap<>();
    private final Map<String, List<Ingredient.Matcher>> ingredientMatchers = new HashMap<>();
    private final Map<String, SpecialDefinition> specialRecipes = new HashMap<>();
    private final Map<String, ResultOverride> resultOverrides = new HashMap<>();
    private final Map<String, SpecialSource> specialSources = new HashMap<>();
    private final Set<NamespacedKey> registeredKeys = new HashSet<>();

    private final Map<String, Set<String>> fileRecipeIds = new HashMap<>();

    private final Map<String, String> fileFingerprints = new HashMap<>();

    public RecipeLoader(Tingere plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public record SpecialSource(int slot, RecipeChoice choice) {
    }

    public record ReloadReport(String relative, int removed, int loaded) {
    }

    public record FullReloadReport(int total, int reloaded, int skippedFiles) {
    }

    private record ChangedFile(String relative, Path path, String fingerprint) {
    }

    public SpecialSource getSpecialSource(String fullKey) {
        return specialSources.get(fullKey);
    }

    public SpecialDefinition getSpecialRecipeInfo(String fullKey) {
        return specialRecipes.get(fullKey);
    }

    public ResultOverride getResultOverride(String fullKey) {
        return resultOverrides.get(fullKey);
    }

    public List<ItemStack> getIngredients(String key) {
        return recipeIngredients.get(key);
    }

    public List<Ingredient.Matcher> getIngredientMatchers(String fullKey) {
        return ingredientMatchers.get(fullKey);
    }

    public Set<NamespacedKey> getRegisteredKeys() {
        return Collections.unmodifiableSet(registeredKeys);
    }

    public Set<String> getAllRecipeKeys() {
        return recipeResults.keySet();
    }

    public ItemStack getResultItem(String key) {
        return recipeResults.get(key);
    }

    public String fileOf(String recipeId) {
        for (Map.Entry<String, Set<String>> entry : fileRecipeIds.entrySet()) {
            if (entry.getValue().contains(recipeId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public List<String> listRecipeFiles() {
        Path base = recipesDirectory();
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(base)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(RecipeLoader::isYamlFile)
                    .map(path -> toRelative(base, path))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to list recipe files", e);
            return List.of();
        }
    }

    public FullReloadReport reloadAll() {
        Path base = recipesDirectory();
        if (!Files.isDirectory(base)) {
            if (base.toFile().mkdirs()) {
                logger.info("Created recipe folder: " + base.toAbsolutePath());
            } else {
                logger.warning("Recipe folder is not a directory: " + base.toAbsolutePath());
            }
            return new FullReloadReport(registeredKeys.size(), 0, 0);
        }

        List<String> present = listRecipeFiles();
        Set<String> presentFiles = new HashSet<>(present);

        List<String> vanished = new ArrayList<>();
        for (String known : new ArrayList<>(fileRecipeIds.keySet())) {
            if (!presentFiles.contains(known)) {
                vanished.add(known);
            }
        }

        List<ChangedFile> changed = new ArrayList<>();
        int skipped = 0;
        for (String relative : present) {
            Path path = base.resolve(relative);
            String fingerprint;
            try {
                fingerprint = fingerprintOf(path);
            } catch (IOException e) {
                logger.warning("Failed to read " + relative + ": " + e.getMessage());
                continue;
            }

            if (fingerprint.equals(fileFingerprints.get(relative))) {
                skipped++;
                continue;
            }
            changed.add(new ChangedFile(relative, path, fingerprint));
        }

        int reloaded = 0;
        if (!vanished.isEmpty() || !changed.isEmpty()) {
            try (RecipeTransaction transaction = RecipeTransaction.begin(plugin)) {
                for (String known : vanished) {
                    int removed = unregisterFile(transaction, known);
                    fileFingerprints.remove(known);
                    logger.info("Unloaded " + removed + " recipe(s) from removed file " + known + ".");
                }
                for (ChangedFile file : changed) {
                    unregisterFile(transaction, file.relative());
                    reloaded += loadRecipesFromFile(transaction, file.path(), file.relative(), file.fingerprint());
                }
            }
        }

        if (skipped > 0) {
            logger.info("Skipped " + skipped + " unchanged recipe file(s).");
        }
        return new FullReloadReport(registeredKeys.size(), reloaded, skipped);
    }

    public ReloadReport reloadFile(String rawRelative) throws IOException {
        String relative = unquote(rawRelative).replace('\\', '/');
        Path file = resolveRecipeFile(relative);

        if (!Files.isRegularFile(file)) {
            throw new NoSuchFileException(relative);
        }
        if (!isYamlFile(file)) {
            throw new NoSuchFileException(relative + " (not a .yml or .yaml file)");
        }

        String fingerprint = fingerprintOf(file);
        try (RecipeTransaction transaction = RecipeTransaction.begin(plugin)) {
            int removed = unregisterFile(transaction, relative);
            int loaded = loadRecipesFromFile(transaction, file, relative, fingerprint);
            return new ReloadReport(relative, removed, loaded);
        }
    }

    public static String unquote(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.startsWith("\"")) {
            value = value.substring(1);
        }
        if (value.length() > 1 && value.endsWith("\"")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private Path recipesDirectory() {
        return plugin.getDataFolder().toPath().resolve(RECIPES_FOLDER).toAbsolutePath().normalize();
    }

    private Path resolveRecipeFile(String relative) throws IOException {
        Path base = recipesDirectory();
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) {
            throw new IOException("'" + relative + "' points outside the recipes folder");
        }
        return resolved;
    }

    private int unregisterFile(RecipeTransaction transaction, String relative) {
        Set<String> ids = fileRecipeIds.remove(relative);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (String id : ids) {
            NamespacedKey key = new NamespacedKey(plugin, id);
            if (transaction.remove(key)) {
                removed++;
            }
            registeredKeys.remove(key);
            recipeResults.remove(id);
            recipeIngredients.remove(id);
            ingredientMatchers.remove(key.toString());
            specialRecipes.remove(key.toString());
            specialSources.remove(key.toString());
            resultOverrides.remove(key.toString());
        }
        return removed;
    }

    private static String toRelative(Path base, Path path) {
        return base.relativize(path).toString().replace('\\', '/');
    }

    private static String fingerprintOf(Path path) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    private static boolean isYamlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private int loadRecipesFromFile(RecipeTransaction transaction, Path path, String relative, String fingerprint) {
        JsonNode root;
        try {
            root = parser.read(path);
        } catch (Exception e) {
            logger.warning("Failed to parse " + relative + ": " + describeException(e));
            return 0;
        }

        fileFingerprints.put(relative, fingerprint);

        if (root.isMissingNode() || root.isNull()) {
            logger.warning("Ignored empty recipe file: " + relative);
            return 0;
        }
        if (!root.isObject()) {
            logger.warning("Ignored " + relative + ": the root must be a mapping");
            return 0;
        }

        Set<String> ids = new HashSet<>();
        int loaded = 0;
        if (root.has("type")) {
            loaded += tryRegister(transaction, root, relative, RecipeParser.labelOf(root, relative), ids) ? 1 : 0;
        } else {
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                loaded += tryRegister(transaction, entry.getValue(), relative, entry.getKey(), ids) ? 1 : 0;
            }
        }

        if (!ids.isEmpty()) {
            fileRecipeIds.put(relative, ids);
        }
        if (loaded > 0) {
            logger.info("Loaded " + loaded + " recipe(s) from " + relative + ".");
        }
        return loaded;
    }

    /**
     * 如果有错误则会跳过并 log
     */
    private boolean tryRegister(RecipeTransaction transaction,
                                JsonNode node,
                                String relative,
                                String label,
                                Set<String> ids) {
        ParseContext ctx = new ParseContext(label, relative);
        try {
            RecipeDefinition definition = parser.bind(node);
            ParseResult<ResolvedRecipe> resolved = ResolvedRecipe.of(plugin, definition, ctx);
            if (ctx.hasErrors()) {
                logger.warning("Skipped recipe '" + label + "' in " + relative + ": "
                        + ctx.errorCount() + " problem(s): " + describe(ctx.problems()));
                return false;
            }
            register(transaction, definition, resolved.value(), ids);
            return true;
        } catch (ParseProblem e) {
            logger.warning("Skipped recipe '" + label + "' in " + relative + ": " + e.describe());
            return false;
        } catch (Exception e) {
            logger.warning("Skipped recipe '" + label + "' in " + relative + ": " + describeException(e));
            return false;
        }
    }

    private static String describe(List<ParseProblem> problems) {
        return problems.stream().map(ParseProblem::describe).collect(Collectors.joining("; "));
    }

    private void register(RecipeTransaction transaction, RecipeDefinition definition, ResolvedRecipe resolved,
                          Set<String> ids) {
        String id = definition.id();
        NamespacedKey namespacedKey = new NamespacedKey(plugin, id);

        transaction.add(definition.toBukkitRecipe(plugin, resolved));
        registeredKeys.add(namespacedKey);
        ids.add(id);

        recipeResults.put(id, resolved.result());
        recipeIngredients.put(id, resolved.ingredientItems());
        ingredientMatchers.put(namespacedKey.toString(), resolved.matchers());

        SpecialDefinition special = definition.special();
        int amount = definition.result().amountOrDefault();
        boolean needsOverriding = definition.resultComponents() != null || amount != Ingredient.DEFAULT_AMOUNT;
        if (needsOverriding && (special != null || definition instanceof TransmuteRecipeDefinition)) {
            resultOverrides.put(namespacedKey.toString(), new ResultOverride(definition.resultComponents(), amount));
        }

        if (special != null) {
            specialRecipes.put(namespacedKey.toString(), special);
            SpecialSource source = resolveSpecialSource(resolved, definition, special, namespacedKey.toString());
            if (source != null) {
                specialSources.put(namespacedKey.toString(), source);
            }
            logger.info("Stored special recipe: " + namespacedKey + " -> " + special.targetMaterial() + " x" + amount
                    + (special.sourceCharacter() != null ? ", sourceChar=" + special.sourceCharacter() : "")
                    + (special.sourceSlot() != null ? ", sourceSlot=" + special.sourceSlot() : ""));
        }
    }

    private SpecialSource resolveSpecialSource(ResolvedRecipe resolved, RecipeDefinition definition,
                                               SpecialDefinition special, String key) {
        if (special.sourceSlot() != null) {
            return new SpecialSource(special.sourceSlot(), null);
        }
        Character symbol = special.sourceCharacterOrNull();
        if (symbol == null || !(definition instanceof ShapedRecipeDefinition shaped)) {
            return null;
        }
        Ingredient source = shaped.ingredients().get(symbol);
        if (source == null) {
            logger.warning("special.source-character '" + symbol + "' is not defined in ingredients of " + key);
            return null;
        }
        return new SpecialSource(slotOf(shaped.pattern(), symbol), source.toRecipeChoice(resolved.of(source)));
    }

    private static int slotOf(List<String> pattern, char symbol) {
        for (int row = 0; row < Math.min(pattern.size(), 3); row++) {
            String line = pattern.get(row);
            for (int col = 0; col < Math.min(line.length(), 3); col++) {
                if (line.charAt(col) == symbol) {
                    return row * 3 + col;
                }
            }
        }
        return -1;
    }

    private static String describeException(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.replaceAll("\\s+at \\[Source:.*$", "").trim();
    }
}
