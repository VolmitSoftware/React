package art.arcane.react.mod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Runtime-to-Mojang names for profiler frames, from the map the build writes for targets that run obfuscated: SRG
 * method names on Forge 1.20.1, intermediary classes and methods on Fabric 1.20.1 and 1.21.1. Jars for Mojang-named
 * runtimes carry no map and every name passes through.
 *
 * <p>The resource is gzip text: a {@code react-frame-names 1} header, a {@code classes <prefix> <count>} section of
 * {@code <key> <name>} lines whose runtime name is the prefix plus the key, then a {@code methods <prefix> <suffix>
 * <count>} section of {@code <delta> <name>} lines, each delta adding to the previous method number.
 */
final class ModFrameNames {
    static final String RESOURCE = "/art/arcane/react/mod/frame-names.gz";
    static final ModFrameNames NONE = new ModFrameNames("", Map.of(), "", "", new int[0], new String[0]);
    private static final String HEADER = "react-frame-names 1";
    private static volatile ModFrameNames loaded;

    private final String classPrefix;
    private final Map<String, String> classes;
    private final String methodPrefix;
    private final int[] ids;
    private final String[] names;
    private final Pattern token;

    private ModFrameNames(String classPrefix, Map<String, String> classes, String methodPrefix, String methodSuffix, int[] ids,
                          String[] names) {
        this.classPrefix = classPrefix;
        this.classes = classes;
        this.methodPrefix = methodPrefix;
        this.ids = ids;
        this.names = names;
        this.token = ids.length == 0 ? null : Pattern.compile(Pattern.quote(methodPrefix) + "(\\d+)" + Pattern.quote(methodSuffix));
    }

    /** The bundled map, read on first use; {@link #NONE} when the jar has none or it cannot be read. */
    static ModFrameNames get() {
        ModFrameNames current = loaded;
        if (current != null) {
            return current;
        }
        synchronized (ModFrameNames.class) {
            if (loaded == null) {
                ModFrameNames read;
                try (InputStream stream = ModFrameNames.class.getResourceAsStream(RESOURCE)) {
                    read = stream == null ? NONE : read(stream);
                } catch (IOException | RuntimeException unreadable) {
                    read = NONE;
                }
                loaded = read;
            }
            return loaded;
        }
    }

    static ModFrameNames read(InputStream gzip) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(gzip), StandardCharsets.UTF_8))) {
            if (!HEADER.equals(reader.readLine())) {
                return NONE;
            }
            String[] classHeader = section(reader.readLine(), "classes", 3);
            String classPrefix = classHeader[1];
            int classCount = Integer.parseInt(classHeader[2]);
            Map<String, String> classes = new HashMap<>(Math.max(16, classCount * 4 / 3 + 1));
            for (int index = 0; index < classCount; index++) {
                String line = reader.readLine();
                int space = line.indexOf(' ');
                classes.put(line.substring(0, space), line.substring(space + 1));
            }
            String[] methodHeader = section(reader.readLine(), "methods", 4);
            int methodCount = Integer.parseInt(methodHeader[3]);
            int[] ids = new int[methodCount];
            String[] names = new String[methodCount];
            int id = 0;
            for (int index = 0; index < methodCount; index++) {
                String line = reader.readLine();
                int space = line.indexOf(' ');
                id += Integer.parseInt(line.substring(0, space));
                ids[index] = id;
                names[index] = line.substring(space + 1);
            }
            return new ModFrameNames(classPrefix, classes, methodHeader[1], methodHeader[2], ids, names);
        }
    }

    private static String[] section(String line, String name, int parts) throws IOException {
        String[] split = line == null ? new String[0] : line.split(" ", -1);
        if (split.length != parts || !split[0].equals(name)) {
            throw new IOException("Expected the " + name + " section");
        }
        return split;
    }

    boolean empty() {
        return classes.isEmpty() && ids.length == 0;
    }

    String readableClass(String className) {
        if (classes.isEmpty() || !className.startsWith(classPrefix)) {
            return className;
        }
        String mapped = classes.get(className.substring(classPrefix.length()));
        return mapped == null ? className : mapped;
    }

    /** Every runtime method number inside the name, so lambdas such as {@code lambda$m_280308_$3} read too. */
    String readableMethod(String methodName) {
        if (token == null || !methodName.contains(methodPrefix)) {
            return methodName;
        }
        Matcher matcher = token.matcher(methodName);
        StringBuilder readable = null;
        while (matcher.find()) {
            int index = Arrays.binarySearch(ids, Integer.parseInt(matcher.group(1)));
            if (index < 0) {
                continue;
            }
            if (readable == null) {
                readable = new StringBuilder(methodName.length());
            }
            matcher.appendReplacement(readable, Matcher.quoteReplacement(names[index]));
        }
        if (readable == null) {
            return methodName;
        }
        matcher.appendTail(readable);
        return readable.toString();
    }
}
