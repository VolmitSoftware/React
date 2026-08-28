package art.arcane.react.api.web.dto;

public record PluginApiPackDto(
    String id,
    String version,
    String name,
    String[] authors,
    String targetPlugin,
    String targetVersion,
    String[] targetVersions,
    boolean enabled,
    boolean trusted,
    String state,
    String detail,
    String fileName,
    String rawContent,
    PluginApiMetricDto[] metrics
) {
}
