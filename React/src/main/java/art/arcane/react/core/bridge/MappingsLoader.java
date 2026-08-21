package art.arcane.react.core.bridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class MappingsLoader {
    public record MappingEntry(List<String> classNames, String memberName) {}

    private static final Logger LOG = Logger.getLogger("React");
    private final Map<String, MappingEntry> entries;

    public MappingsLoader() {
        this(detectBukkitVersion());
    }

    public MappingsLoader(String mcVersion) {
        Map<String, MappingEntry> loaded = tryLoad(mcVersion);
        if (loaded == null) {
            int dot = mcVersion.lastIndexOf('.');
            String major = dot > 0 ? mcVersion.substring(0, dot) : mcVersion;
            loaded = tryLoad(major);
        }
        this.entries = loaded != null ? loaded : Map.of();
    }

    public MappingsLoader(InputStream stream) {
        Map<String, MappingEntry> loaded = parseStream(stream);
        this.entries = loaded != null ? loaded : Map.of();
    }

    public MappingEntry lookup(String mappingsKey) {
        return entries.get(mappingsKey);
    }

    private static String detectBukkitVersion() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            java.lang.reflect.Method m = bukkit.getMethod("getMinecraftVersion");
            Object result = m.invoke(null);
            if (result instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (Throwable ignored) {
        }
        return "1.21";
    }

    private static Map<String, MappingEntry> tryLoad(String version) {
        String path = "/mappings/" + version + ".json";
        InputStream stream = MappingsLoader.class.getResourceAsStream(path);
        if (stream == null) {
            return null;
        }
        Map<String, MappingEntry> result = parseStream(stream);
        if (result == null) {
            LOG.warning("[React] Mappings file " + path + " could not be parsed — using fuzzy name resolution only.");
        }
        return result;
    }

    private static Map<String, MappingEntry> parseStream(InputStream stream) {
        if (stream == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject rootObj = root.getAsJsonObject();
            if (!rootObj.has("members") || !rootObj.get("members").isJsonObject()) {
                return Map.of();
            }
            JsonObject members = rootObj.getAsJsonObject("members");
            Map<String, MappingEntry> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> memberEntry : members.entrySet()) {
                String key = memberEntry.getKey();
                JsonElement value = memberEntry.getValue();
                if (!value.isJsonObject()) {
                    continue;
                }
                JsonObject memberObj = value.getAsJsonObject();
                String memberName = memberObj.has("memberName")
                        ? memberObj.get("memberName").getAsString() : null;
                List<String> classNames = new ArrayList<>();
                if (memberObj.has("classNames") && memberObj.get("classNames").isJsonArray()) {
                    for (JsonElement elem : memberObj.getAsJsonArray("classNames")) {
                        classNames.add(elem.getAsString());
                    }
                }
                if (memberName != null) {
                    result.put(key, new MappingEntry(List.copyOf(classNames), memberName));
                }
            }
            return Map.copyOf(result);
        } catch (Throwable t) {
            return null;
        }
    }
}
