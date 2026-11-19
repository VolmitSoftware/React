package art.arcane.react.api.web.dto;

public record PluginApiCatalogDto(
    String folder,
    PluginApiPackDto[] packs,
    PluginApiValidationErrorDto[] errors
) {
}
