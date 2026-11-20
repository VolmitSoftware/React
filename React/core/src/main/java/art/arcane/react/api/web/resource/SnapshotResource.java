package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.WebError;
import art.arcane.react.api.web.WebMutation;
import art.arcane.react.api.web.WebMutationReporter;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.heatmap.HeatmapCellProvider;
import art.arcane.react.api.web.heatmap.HeatmapDto;
import art.arcane.react.api.web.profiler.ProfileDtos;
import art.arcane.react.api.web.profiler.SamplingProfiler;
import art.arcane.react.api.web.profiler.SnapshotStore;
import io.javalin.http.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** {@code /api/v1/snapshots}: frozen profiles and heatmaps, listed, read, exported as gzip JSON and deleted. */
public final class SnapshotResource {
  private static final byte[] DATA_OPEN = "{\"data\":".getBytes(StandardCharsets.UTF_8);
  private final Supplier<SnapshotStore> store;
  private final SamplingProfiler profiler;
  private final HeatmapCellProvider heatmaps;
  private final Supplier<String> serverName;
  private final WebMutationReporter reporter;

  public SnapshotResource(Supplier<SnapshotStore> store, SamplingProfiler profiler, HeatmapCellProvider heatmaps,
                          Supplier<String> serverName, WebMutationReporter reporter) {
    this.store = store;
    this.profiler = profiler;
    this.heatmaps = heatmaps;
    this.serverName = serverName;
    this.reporter = reporter;
  }

  public record CreateBody(String kind, String label, Map<String, Object> heatmap) {
  }

  public void create(Context ctx) throws IOException {
    WebAuth.requireScope(ctx, "admin");
    CreateBody body = ctx.bodyAsClass(CreateBody.class);
    String kind = body == null || body.kind() == null ? "" : body.kind().trim().toLowerCase(Locale.ROOT);
    String label = body == null ? null : body.label();
    if (label != null && label.strip().length() > SnapshotStore.MAX_LABEL) {
      throw WebError.badRequest("label is at most " + SnapshotStore.MAX_LABEL + " characters");
    }
    SnapshotStore.Meta meta = switch (kind) {
      case SnapshotStore.PROFILE -> {
        ProfileDtos.Profile profile = profiler.profile();
        if (profile.profiler().samples() == 0) {
          throw new WebError(409, "conflict", "The profiler has no samples yet");
        }
        yield store.get().save(SnapshotStore.PROFILE, label, serverName.get(), SnapshotStore.profileSummary(profile), profile);
      }
      case SnapshotStore.HEATMAP -> {
        HeatmapDto heatmap = heatmap(body.heatmap());
        yield store.get().save(SnapshotStore.HEATMAP, label, serverName.get(), SnapshotStore.heatmapSummary(heatmap), heatmap);
      }
      default -> throw WebError.badRequest("kind must be profile or heatmap");
    };
    reporter.report(ctx, new WebMutation("snapshot.create", "snapshot:" + meta.id(), meta.kind() + " " + meta.label(), "APPLIED"));
    ctx.json(new Envelope<>(meta));
  }

  public void list(Context ctx) {
    ctx.json(new Envelope<>(new SnapshotStore.Listing(store.get().list(ctx.queryParam("kind")))));
  }

  public void get(Context ctx) throws IOException {
    byte[] document = store.get().document(ctx.pathParam("id")).orElseThrow(SnapshotResource::missing);
    ByteArrayOutputStream enveloped = new ByteArrayOutputStream(document.length + 10);
    enveloped.write(DATA_OPEN);
    enveloped.write(document);
    enveloped.write('}');
    ctx.contentType("application/json").result(enveloped.toByteArray());
  }

  public void export(Context ctx) throws IOException {
    String id = ctx.pathParam("id");
    SnapshotStore current = store.get();
    SnapshotStore.Meta meta = current.meta(id).orElseThrow(SnapshotResource::missing);
    byte[] gzip = current.export(id).orElseThrow(SnapshotResource::missing);
    ctx.header("Content-Disposition", "attachment; filename=\"" + current.exportName(meta) + "\"");
    ctx.contentType("application/gzip").result(gzip);
  }

  public void delete(Context ctx) throws IOException {
    WebAuth.requireScope(ctx, "admin");
    String id = ctx.pathParam("id");
    SnapshotStore current = store.get();
    SnapshotStore.Meta meta = current.meta(id).orElseThrow(SnapshotResource::missing);
    if (!current.delete(id)) {
      throw missing();
    }
    reporter.report(ctx, new WebMutation("snapshot.delete", "snapshot:" + id, "deleted", "APPLIED"));
    ctx.json(new Envelope<>(meta));
  }

  private HeatmapDto heatmap(Map<String, Object> params) {
    if (params == null || !(params.get("id") instanceof String id) || id.isBlank()) {
      throw WebError.badRequest("heatmap.id is required");
    }
    Object world = params.get("world");
    HeatmapDto dto;
    try {
      dto = heatmaps.compute(id, world instanceof String name && !name.isBlank() ? name : null,
          integer(params, "centerChunkX"), integer(params, "centerChunkZ"), integer(params, "radius"));
    } catch (IllegalArgumentException invalid) {
      throw WebError.badRequest("Invalid heatmap parameters");
    }
    if (dto == null) {
      throw new WebError(404, "not_found", "Unknown heatmap: " + id);
    }
    return dto;
  }

  private static Integer integer(Map<String, Object> params, String key) {
    Object value = params.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(value.toString().trim());
    } catch (NumberFormatException invalid) {
      throw WebError.badRequest("heatmap." + key + " must be a whole number");
    }
  }

  private static WebError missing() {
    return new WebError(404, "not_found", "Unknown snapshot");
  }
}
