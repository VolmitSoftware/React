package art.arcane.react.api.web.dto;

public record PluginApiValidationResultDto(boolean valid, String id, int metricCount, String message) {
}
