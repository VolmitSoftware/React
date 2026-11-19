package art.arcane.react.mod;

import art.arcane.react.platform.RegistryHost;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Registries find their classes through {@value #CLASS_INDEX}, written into the jar at build time, because mod loaders
 * do not expose React's jar as a plain file. Config files live under React's data folder as on Bukkit.
 */
final class ModRegistryHost implements RegistryHost {
    static final String CLASS_INDEX = "react-class-index.txt";
    private final File dataFolder;
    private final List<Runnable> startupTasks;
    private final Consumer<String> info;
    private final Consumer<String> warn;
    private volatile List<String> index;

    ModRegistryHost(File dataFolder, List<Runnable> startupTasks, Consumer<String> info, Consumer<String> warn) {
        this.dataFolder = dataFolder;
        this.startupTasks = startupTasks;
        this.info = info;
        this.warn = warn;
    }

    @Override
    public Collection<Class<?>> discoverClasses(String packageName) throws IOException {
        String prefix = packageName + ".";
        ClassLoader loader = ModRegistryHost.class.getClassLoader();
        List<Class<?>> classes = new ArrayList<>();
        for (String name : index()) {
            if (!name.startsWith(prefix)) {
                continue;
            }
            try {
                classes.add(Class.forName(name, true, loader));
            } catch (Throwable unusable) {
                warn.accept("Skipped " + name + ": " + unusable);
            }
        }
        return classes;
    }

    private List<String> index() throws IOException {
        List<String> current = index;
        if (current != null) {
            return current;
        }
        InputStream stream = ModRegistryHost.class.getClassLoader().getResourceAsStream(CLASS_INDEX);
        if (stream == null) {
            throw new IOException(CLASS_INDEX + " is missing from the React jar");
        }
        List<String> names = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank()) {
                    names.add(line.strip());
                }
            }
        }
        index = List.copyOf(names);
        return index;
    }

    @Override
    public void deferStartupTask(Runnable task) {
        startupTasks.add(task);
    }

    @Override
    public File configFile(String category, String fileName) {
        File folder = new File(dataFolder, category);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            warn.accept("Could not create " + folder);
        }
        return new File(folder, fileName);
    }

    @Override
    public Object loadConfiguration(File file, Class<?> type, Object defaults, boolean overwriteOnReadFailure, String sourceTag,
                                    String createdMessage) throws IOException {
        return ModConfigFiles.load(file, type, defaults, overwriteOnReadFailure, message -> info.accept(sourceTag + ": " + message));
    }

    @Override
    public Object parseConfiguration(File sourceFile, String rawContent, Class<?> type, String sourceTag) throws IOException {
        return ModConfigFiles.parse(rawContent, type);
    }
}
