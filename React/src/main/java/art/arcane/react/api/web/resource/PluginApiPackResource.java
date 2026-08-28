package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.WebMutation;
import art.arcane.react.api.web.WebMutationReporter;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.PluginApiCatalogDto;
import art.arcane.react.api.web.dto.PluginApiMetricDto;
import art.arcane.react.api.web.dto.PluginApiPackDto;
import art.arcane.react.api.web.dto.PluginApiValidationErrorDto;
import art.arcane.react.api.web.dto.PluginApiValidationResultDto;
import art.arcane.react.core.controller.PluginApiPackController;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime.MetricStatus;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime.PackStatus;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.ServiceUnavailableResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class PluginApiPackResource {
  private final Supplier<PluginApiPackController> controllerSupplier;
  private final WebMutationReporter reporter;

  public PluginApiPackResource(
      Supplier<PluginApiPackController> controllerSupplier,
      WebMutationReporter reporter
  ) {
    this.controllerSupplier = controllerSupplier;
    this.reporter = reporter;
  }

  public void list(Context context) {
    context.json(new Envelope<>(catalog(controller())));
  }

  public void get(Context context) {
    PackStatus status = controller().status(context.pathParam("id"));
    if (status == null) {
      throw new NotFoundResponse("Unknown Plugin API pack: " + context.pathParam("id"));
    }
    context.json(new Envelope<>(pack(status)));
  }

  public void validate(Context context) {
    WebAuth.requireScope(context, "admin");
    ContentBody body = body(context);
    PluginApiPackController.ValidationResult result = controller().validate(body.content());
    context.json(new Envelope<>(new PluginApiValidationResultDto(
        result.valid(),
        result.id(),
        result.metricCount(),
        result.message()
    )));
  }

  public void put(Context context) {
    WebAuth.requireScope(context, "admin");
    ContentBody body = body(context);
    PluginApiPackController controller = controller();
    PluginApiPackController.ValidationResult validation = controller.validate(body.content());
    if (!validation.valid()) {
      throw new BadRequestResponse(validation.message());
    }
    try {
      PackStatus status = controller.install(context.pathParam("id"), body.content());
      reporter.report(context, new WebMutation(
          "plugin-api-pack.install",
          "plugin-api-pack:" + status.id(),
          "installed pack version " + status.version(),
          "APPLIED"
      ));
      context.json(new Envelope<>(pack(status)));
    } catch (IOException failure) {
      throw new InternalServerErrorResponse("Failed to install Plugin API pack: " + failure.getMessage());
    }
  }

  public void delete(Context context) {
    WebAuth.requireScope(context, "admin");
    PluginApiPackController controller = controller();
    String id = context.pathParam("id");
    try {
      if (!controller.remove(id)) {
        throw new NotFoundResponse("Unknown Plugin API pack: " + id);
      }
    } catch (IOException failure) {
      throw new InternalServerErrorResponse("Failed to remove Plugin API pack: " + failure.getMessage());
    }
    reporter.report(context, new WebMutation(
        "plugin-api-pack.remove",
        "plugin-api-pack:" + id,
        "removed pack",
        "APPLIED"
    ));
    context.json(new Envelope<>(catalog(controller)));
  }

  private PluginApiPackController controller() {
    PluginApiPackController controller = controllerSupplier.get();
    if (controller == null) {
      throw new ServiceUnavailableResponse("Plugin API pack controller is not ready");
    }
    return controller;
  }

  private ContentBody body(Context context) {
    ContentBody body = context.bodyAsClass(ContentBody.class);
    if (body == null || body.content() == null || body.content().isBlank()) {
      throw new BadRequestResponse("Missing pack content");
    }
    return body;
  }

  private PluginApiCatalogDto catalog(PluginApiPackController controller) {
    List<PackStatus> statuses = controller.statuses();
    PluginApiPackDto[] packs = new PluginApiPackDto[statuses.size()];
    for (int index = 0; index < statuses.size(); index++) {
      packs[index] = pack(statuses.get(index));
    }
    Map<String, String> errors = controller.validationErrors();
    PluginApiValidationErrorDto[] validationErrors = new PluginApiValidationErrorDto[errors.size()];
    int index = 0;
    for (Map.Entry<String, String> entry : errors.entrySet()) {
      validationErrors[index++] = new PluginApiValidationErrorDto(entry.getKey(), entry.getValue());
    }
    String folder = controller.packFolder() == null ? "" : controller.packFolder().toString();
    return new PluginApiCatalogDto(folder, packs, validationErrors);
  }

  private PluginApiPackDto pack(PackStatus status) {
    List<MetricStatus> statuses = status.metrics();
    PluginApiMetricDto[] metrics = new PluginApiMetricDto[statuses.size()];
    for (int index = 0; index < statuses.size(); index++) {
      MetricStatus metric = statuses.get(index);
      metrics[index] = new PluginApiMetricDto(
          metric.id(),
          metric.samplerId(),
          metric.displayName(),
          metric.sourceType(),
          metric.available(),
          metric.availabilityReason(),
          metric.sampledAtMs(),
          metric.sampleDurationMs(),
          metric.acceptedSamples(),
          metric.failedSamples(),
          metric.quarantined()
      );
    }
    return new PluginApiPackDto(
        status.id(),
        status.version(),
        status.name(),
        status.authors().toArray(new String[0]),
        status.targetPlugin(),
        status.targetVersion(),
        status.targetVersions().toArray(new String[0]),
        status.enabled(),
        status.trusted(),
        status.state().name(),
        status.detail(),
        status.fileName(),
        status.rawContent(),
        metrics
    );
  }

  public record ContentBody(String content) {
  }
}
