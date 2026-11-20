package art.arcane.react.util.project.config;


import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * React's TOML config files outside Bukkit, read and written by the same codec as on Bukkit: the same keys, layout and
 * documentation comments. Loading rewrites a file into its canonical form, which fills in missing keys from the defaults.
 */
public final class TomlFiles {
    private TomlFiles() {
    }

    public static <T> T load(File file, Class<T> type, Object defaults, String sourceTag, boolean overwriteOnReadFailure,
                      Consumer<String> info) throws IOException {
        T fallback = defaults == null ? TomlCodec.fromToml("", type) : type.cast(defaults);
        if (!file.isFile()) {
            write(file, TomlCodec.toToml(fallback, sourceTag));
            info.accept("Created missing config [" + file.getName() + "] from defaults.");
            return fallback;
        }
        String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        T loaded;
        try {
            loaded = parse(raw, type);
        } catch (IOException failure) {
            if (!overwriteOnReadFailure) {
                throw failure;
            }
            Path backup = file.toPath().resolveSibling(file.getName() + ".bak");
            Files.copy(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            write(file, TomlCodec.toToml(fallback, sourceTag));
            info.accept("Config [" + file.getName() + "] could not be parsed and was reset to defaults. Your previous values are preserved in ["
                    + backup.getFileName() + "].");
            return fallback;
        }
        String canonical = TomlCodec.toToml(loaded, sourceTag);
        if (!normalize(canonical).equals(normalize(raw))) {
            write(file, canonical);
        }
        return loaded;
    }

    public static void save(File file, Object value, String sourceTag) throws IOException {
        write(file, TomlCodec.toToml(value, sourceTag));
    }

    public static <T> T parse(String raw, Class<T> type) throws IOException {
        T parsed = TomlCodec.fromToml(raw, type);
        if (parsed == null) {
            throw new IOException("Config parser returned null.");
        }
        return parsed;
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").stripTrailing();
    }

    private static void write(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        Path temporary = file.toPath().resolveSibling(file.getName() + ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
