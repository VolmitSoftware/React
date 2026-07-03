package art.arcane.react.api.web;

import com.moandjiezana.toml.Toml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TokenStore {

  private final ConcurrentHashMap<String, TokenRecord> records = new ConcurrentHashMap<>();

  private TokenStore() {
  }

  public Optional<TokenRecord> lookup(String id) {
    return Optional.ofNullable(records.get(id));
  }

  public List<TokenRecord> all() {
    return new ArrayList<>(records.values());
  }

  public void add(TokenRecord record) {
    records.put(record.id(), record);
  }

  public void remove(String id) {
    records.remove(id);
  }

  public void save(File tomlFile) throws IOException {
    File parent = tomlFile.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }
    try (PrintWriter pw = new PrintWriter(
        new OutputStreamWriter(new FileOutputStream(tomlFile, false), StandardCharsets.UTF_8))) {
      for (TokenRecord record : all()) {
        pw.println("[[tokens]]");
        pw.println("id = \"" + escapeToml(record.id()) + "\"");
        pw.println("label = \"" + escapeToml(record.label()) + "\"");
        pw.println("issuedAt = " + record.issuedAt());
        pw.print("scopes = [");
        List<String> sortedScopes = new ArrayList<>(record.scopes());
        Collections.sort(sortedScopes);
        for (int i = 0; i < sortedScopes.size(); i++) {
          if (i > 0) {
            pw.print(", ");
          }
          pw.print("\"" + escapeToml(sortedScopes.get(i)) + "\"");
        }
        pw.println("]");
        if (record.role() != null) {
          pw.println("role = \"" + escapeToml(record.role()) + "\"");
        }
        pw.println();
      }
    }
  }

  public static TokenStore inMemory(TokenRecord... records) {
    TokenStore store = new TokenStore();
    for (TokenRecord record : records) {
      store.add(record);
    }
    return store;
  }

  public static TokenStore fromToml(File tomlFile) throws IOException {
    TokenStore store = new TokenStore();
    if (!tomlFile.exists()) {
      return store;
    }
    Toml toml;
    try {
      toml = new Toml().read(tomlFile);
    } catch (RuntimeException e) {
      throw new IOException("Failed to parse token store: " + tomlFile.getAbsolutePath(), e);
    }
    List<Toml> tables = toml.getTables("tokens");
    if (tables != null) {
      for (Toml table : tables) {
        String id = table.getString("id");
        if (id == null) {
          continue;
        }
        String label = table.getString("label");
        Long issuedAtBoxed = table.getLong("issuedAt");
        long issuedAt = issuedAtBoxed != null ? issuedAtBoxed : 0L;
        List<String> scopeList = table.<String>getList("scopes");
        Set<String> scopes = scopeList != null ? new HashSet<>(scopeList) : new HashSet<>();
        String role = table.getString("role");
        store.add(new TokenRecord(id, label != null ? label : "", issuedAt, scopes, role));
      }
    }
    return store;
  }

  private static String escapeToml(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
