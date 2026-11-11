package art.arcane.react.packaging;

import art.arcane.react.React;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.ErrorEnvelope;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import io.javalin.json.JsonMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Curse used to pull jackson-databind in for Javalin. Javalin answers every ctx.json call with a
 * 500 when no ObjectMapper is on the classpath, so the mapper must resolve and must be able to
 * write each response type the web API returns.
 */
class WebJsonMapperTest {
  private static final String WEB_PACKAGE = "art.arcane.react.api.web.";
  private static final int MAX_DEPTH = 3;

  private static List<Class<?>> responseTypes;

  @BeforeAll
  static void findResponseTypes() throws IOException, URISyntaxException {
    Path root = Path.of(React.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Path webRoot = root.resolve(WEB_PACKAGE.replace('.', '/'));
    List<Class<?>> types = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(webRoot)) {
      for (Path file : paths.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
        String relative = root.relativize(file).toString();
        String name = relative.substring(0, relative.length() - ".class".length()).replace('/', '.').replace('\\', '.');
        Class<?> type;
        try {
          type = Class.forName(name, false, React.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError unavailable) {
          continue;
        }
        if (isResponseType(type)) {
          types.add(type);
        }
      }
    }
    responseTypes = types;
  }

  private static boolean isResponseType(Class<?> type) {
    int modifiers = type.getModifiers();
    if (!Modifier.isPublic(modifiers) || Modifier.isAbstract(modifiers) || type.isInterface() || type.isEnum()
        || type.isAnonymousClass() || type == Envelope.class) {
      return false;
    }
    boolean dto = type.getName().startsWith(WEB_PACKAGE + "dto.") || type.getSimpleName().endsWith("Dto");
    boolean resourceRecord = type.isRecord() && type.getName().startsWith(WEB_PACKAGE + "resource.");
    return dto || resourceRecord;
  }

  private static Object sample(Class<?> type, int depth) {
    if (type == boolean.class || type == Boolean.class) {
      return Boolean.TRUE;
    }
    if (type == int.class || type == Integer.class) {
      return 1;
    }
    if (type == long.class || type == Long.class) {
      return 1L;
    }
    if (type == double.class || type == Double.class) {
      return 1.5D;
    }
    if (type == float.class || type == Float.class) {
      return 1.5F;
    }
    if (type == short.class || type == Short.class) {
      return (short) 1;
    }
    if (type == byte.class || type == Byte.class) {
      return (byte) 1;
    }
    if (type == char.class || type == Character.class) {
      return 'c';
    }
    if (type == String.class) {
      return "sample";
    }
    if (type.isEnum()) {
      return type.getEnumConstants().length == 0 ? null : type.getEnumConstants()[0];
    }
    if (type.isArray()) {
      Object element = depth < MAX_DEPTH ? sample(type.getComponentType(), depth + 1) : null;
      Object array = Array.newInstance(type.getComponentType(), element == null ? 0 : 1);
      if (element != null) {
        Array.set(array, 0, element);
      }
      return array;
    }
    if (type == List.class) {
      return new ArrayList<>();
    }
    if (type == Set.class) {
      return Set.of();
    }
    if (type == Map.class) {
      return new LinkedHashMap<>();
    }
    if (depth >= MAX_DEPTH || !type.getName().startsWith("art.arcane.react.")) {
      return null;
    }
    try {
      if (type.isRecord()) {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
          parameterTypes[i] = components[i].getType();
          arguments[i] = sample(components[i].getType(), depth + 1);
        }
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
      }
      Constructor<?> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  private static JsonMapper javalinJackson() throws ReflectiveOperationException {
    return (JsonMapper) Class.forName("io.javalin.json.JavalinJackson").getDeclaredConstructor().newInstance();
  }

  private static List<Object> samples() {
    List<Object> samples = new ArrayList<>();
    for (Class<?> type : responseTypes) {
      Object sample = sample(type, 0);
      assertNotNull(sample, "Cannot build a sample " + type.getName());
      samples.add(sample);
    }
    return samples;
  }

  @Test
  void jacksonDatabindResolvesAtTheSameReleaseAsJacksonCore() throws ReflectiveOperationException {
    Class<?> databindVersion = Class.forName("com.fasterxml.jackson.databind.cfg.PackageVersion");
    Class<?> coreVersion = Class.forName("com.fasterxml.jackson.core.json.PackageVersion");
    Class.forName("com.fasterxml.jackson.databind.ObjectMapper").getDeclaredConstructor().newInstance();

    assertEquals(coreVersion.getField("VERSION").get(null).toString(), databindVersion.getField("VERSION").get(null).toString());
  }

  @Test
  void buildScriptShipsJacksonDatabindAtTheAnnotationsVersion() throws IOException {
    String buildScript = Files.readString(Path.of("build.gradle"));
    Matcher databind = Pattern.compile("slim\\('com\\.fasterxml\\.jackson\\.core:jackson-databind:([^']+)'\\)").matcher(buildScript);
    Matcher annotations = Pattern.compile("compileOnly\\('com\\.fasterxml\\.jackson\\.core:jackson-annotations:([^']+)'\\)").matcher(buildScript);

    assertTrue(databind.find(), "jackson-databind is not a slim dependency");
    assertTrue(annotations.find(), "jackson-annotations is not declared");
    assertEquals(annotations.group(1), databind.group(1));
    assertTrue(buildScript.contains("slim('io.javalin:javalin:"));
  }

  @Test
  void findsTheResponseTypesTheResourcesReturn() {
    List<String> names = responseTypes.stream().map(Class::getName).toList();

    assertTrue(names.contains(ErrorEnvelope.class.getName()), names.toString());
    assertTrue(names.contains("art.arcane.react.api.web.dto.SamplerDto"), names.toString());
    assertTrue(names.contains("art.arcane.react.api.web.resource.MetricsResource$SnapshotResponse"), names.toString());
    assertTrue(names.contains("art.arcane.react.api.web.resource.ActionResource$ListResponse"), names.toString());
    assertTrue(names.size() >= 40, "Only found " + names.size() + " response types");
  }

  @Test
  void javalinJacksonWritesEveryResponseType() throws ReflectiveOperationException {
    JsonMapper mapper = javalinJackson();

    for (Object sample : samples()) {
      String json = mapper.toJsonString(new Envelope<>(sample), Envelope.class);
      JsonElement parsed = JsonParser.parseString(json);
      assertTrue(parsed.getAsJsonObject().has("data"), sample.getClass().getName() + " -> " + json);
      assertFalse(parsed.getAsJsonObject().get("data").isJsonNull(), sample.getClass().getName() + " -> " + json);
    }
  }

  @Test
  void javalinServerAnswersEveryResponseTypeAsJson() throws Exception {
    List<Object> samples = samples();
    JsonMapper mapper = javalinJackson();
    Javalin app = Javalin.create();
    app.get("/dto/{index}", context -> context.json(new Envelope<>(samples.get(Integer.parseInt(context.pathParam("index"))))));
    app.get("/error", context -> context.status(404).json(new ErrorEnvelope(new ErrorEnvelope.Message("missing"))));
    app.start("127.0.0.1", 0);
    try {
      HttpClient client = HttpClient.newHttpClient();
      String base = "http://127.0.0.1:" + app.port();
      for (int i = 0; i < samples.size(); i++) {
        String typeName = samples.get(i).getClass().getName();
        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(URI.create(base + "/dto/" + i)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, response.statusCode(), typeName + " -> " + response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"), typeName);
        assertEquals(mapper.toJsonString(new Envelope<>(samples.get(i)), Envelope.class), response.body(), typeName);
      }

      HttpResponse<String> error = client.send(
          HttpRequest.newBuilder(URI.create(base + "/error")).GET().build(),
          HttpResponse.BodyHandlers.ofString()
      );
      assertEquals(404, error.statusCode());
      assertEquals("missing", JsonParser.parseString(error.body()).getAsJsonObject()
          .getAsJsonObject("error").get("message").getAsString());
    } finally {
      app.stop();
    }
  }
}
