package art.arcane.react.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * React's TOML config files on a mod server: the same keys and layout as on Bukkit, read through Gson so field types
 * match, with missing keys filled from the defaults and written back. Comments are not written.
 */
final class ModConfigFiles {
    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    private ModConfigFiles() {
    }

    static <T> T load(File file, Class<T> type, Object defaults, boolean overwriteOnReadFailure, Consumer<String> info)
            throws IOException {
        JsonObject defaultTree = defaults == null ? new JsonObject() : asObject(GSON.toJsonTree(defaults));
        if (!file.isFile()) {
            write(file, defaultTree);
            info.accept("Created missing config [" + file.getName() + "] from defaults.");
            return GSON.fromJson(defaultTree, type);
        }
        String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JsonObject stored;
        try {
            stored = parse(raw);
        } catch (IOException failure) {
            if (!overwriteOnReadFailure) {
                throw failure;
            }
            Path backup = file.toPath().resolveSibling(file.getName() + ".broken");
            Files.copy(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            write(file, defaultTree);
            info.accept("Config [" + file.getName() + "] could not be read; kept it as " + backup.getFileName() + " and wrote defaults.");
            return GSON.fromJson(defaultTree, type);
        }
        if (mergeMissing(stored, defaultTree)) {
            write(file, stored);
        }
        return GSON.fromJson(stored, type);
    }

    static void save(File file, Object value) throws IOException {
        write(file, asObject(GSON.toJsonTree(value)));
    }

    static <T> T parse(String raw, Class<T> type) throws IOException {
        return GSON.fromJson(parse(raw), type);
    }

    static JsonObject parse(String raw) throws IOException {
        try {
            Map<String, Object> map = new Toml().read(raw == null ? "" : raw).toMap();
            return map == null ? new JsonObject() : asObject(GSON.toJsonTree(map));
        } catch (RuntimeException failure) {
            throw new IOException("Invalid toml: " + failure.getMessage(), failure);
        }
    }

    private static boolean mergeMissing(JsonObject target, JsonObject defaults) {
        boolean changed = false;
        for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
            JsonElement current = target.get(entry.getKey());
            if (current == null) {
                target.add(entry.getKey(), entry.getValue().deepCopy());
                changed = true;
            } else if (current.isJsonObject() && entry.getValue().isJsonObject()) {
                changed |= mergeMissing(current.getAsJsonObject(), entry.getValue().getAsJsonObject());
            }
        }
        return changed;
    }

    private static void write(File file, JsonObject tree) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        String text = new TomlWriter.Builder().indentValuesBy(0).build().write(toPlain(tree));
        Path temporary = file.toPath().resolveSibling(file.getName() + ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static Object toPlain(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                Object value = toPlain(entry.getValue());
                if (value != null) {
                    map.put(entry.getKey(), value);
                }
            }
            return map;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            List<Object> list = new ArrayList<>(array.size());
            for (JsonElement item : array) {
                Object value = toPlain(item);
                if (value != null) {
                    list.add(value);
                }
            }
            return list;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            Number number = primitive.getAsNumber();
            if (number instanceof Double || number instanceof Float) {
                return number.doubleValue();
            }
            String text = primitive.getAsString();
            return text.contains(".") || text.contains("e") || text.contains("E") ? (Object) number.doubleValue() : (Object) number.longValue();
        }
        return primitive.getAsString();
    }

    private static JsonObject asObject(JsonElement element) throws IOException {
        if (element == null || !element.isJsonObject()) {
            throw new IOException("Expected a table of settings");
        }
        return element.getAsJsonObject();
    }
}
