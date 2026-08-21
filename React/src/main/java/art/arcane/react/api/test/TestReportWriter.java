package art.arcane.react.api.test;

import art.arcane.react.React;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class TestReportWriter {
  private TestReportWriter() {
  }

  public static Path write(TestReport report, String kind) throws Exception {
    File dir = new File(React.instance.getDataFolder(), "test-reports");
    dir.mkdirs();
    String safePlatform = report.platform() == null ? "unknown" : report.platform().replaceAll("[^a-zA-Z0-9_.-]", "_");
    File file = new File(dir, report.startedAtMillis() + "-" + safePlatform + "-" + kind + ".json");
    String json = new GsonBuilder().setPrettyPrinting().create().toJson(toJson(report, kind));
    Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
    return file.toPath();
  }

  public static JsonObject toJson(TestReport report, String kind) {
    JsonObject root = new JsonObject();
    root.addProperty("kind", kind);
    root.addProperty("platform", report.platform());
    root.addProperty("mcVersion", report.mcVersion());
    root.addProperty("foliaThreading", report.foliaThreading());
    root.addProperty("full", report.full());
    root.addProperty("bridgeAvailable", report.bridgeAvailable());
    root.addProperty("bridgeUnavailable", report.bridgeUnavailable());
    root.addProperty("startedAt", report.startedAtMillis());
    root.addProperty("durationMs", System.currentTimeMillis() - report.startedAtMillis());
    root.addProperty("passed", report.passed());

    JsonObject counts = new JsonObject();
    counts.addProperty("pass", report.count(TestStatus.PASS));
    counts.addProperty("fail", report.count(TestStatus.FAIL));
    counts.addProperty("warn", report.count(TestStatus.WARN));
    counts.addProperty("skip", report.count(TestStatus.SKIP));
    counts.addProperty("info", report.count(TestStatus.INFO));
    root.add("counts", counts);

    JsonArray checks = new JsonArray();
    for (TestCheck check : report.checks()) {
      JsonObject node = new JsonObject();
      node.addProperty("subsystem", check.subsystem());
      node.addProperty("name", check.name());
      node.addProperty("status", check.status().name());
      node.addProperty("detail", check.detail());
      if (check.data() != null) {
        JsonObject data = new JsonObject();
        for (Map.Entry<String, Object> entry : check.data().entrySet()) {
          Object value = entry.getValue();
          if (value instanceof Number number) {
            data.add(entry.getKey(), new JsonPrimitive(number));
          } else if (value instanceof Boolean flag) {
            data.add(entry.getKey(), new JsonPrimitive(flag));
          } else {
            data.add(entry.getKey(), new JsonPrimitive(String.valueOf(value)));
          }
        }
        node.add("data", data);
      }
      checks.add(node);
    }
    root.add("checks", checks);
    return root;
  }
}
