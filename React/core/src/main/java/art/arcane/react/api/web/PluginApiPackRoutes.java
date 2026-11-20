package art.arcane.react.api.web;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.PluginApiCatalogDto;
import art.arcane.react.api.web.dto.PluginApiPackDto;
import art.arcane.react.api.web.dto.PluginApiValidationErrorDto;
import art.arcane.react.localization.ReactLanguage;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

/** The {@code /api/v1/plugin-api-packs} handlers. A platform without Plugin API packs lists none. */
public interface PluginApiPackRoutes {
  void list(Context context);

  void get(Context context);

  void validate(Context context);

  void put(Context context);

  void delete(Context context);

  PluginApiPackRoutes NONE = new PluginApiPackRoutes() {
    @Override
    public void list(Context context) {
      context.json(new Envelope<>(new PluginApiCatalogDto("", new PluginApiPackDto[0], new PluginApiValidationErrorDto[0])));
    }

    @Override
    public void get(Context context) {
      throw new NotFoundResponse(ReactLanguage.owned("Unknown Plugin API pack: ") + context.pathParam("id"));
    }

    @Override
    public void validate(Context context) {
      WebAuth.requireScope(context, "admin");
      throw new NotFoundResponse(ReactLanguage.owned("Plugin API packs are not available on this platform"));
    }

    @Override
    public void put(Context context) {
      WebAuth.requireScope(context, "admin");
      throw new NotFoundResponse(ReactLanguage.owned("Plugin API packs are not available on this platform"));
    }

    @Override
    public void delete(Context context) {
      WebAuth.requireScope(context, "admin");
      throw new NotFoundResponse(ReactLanguage.owned("Unknown Plugin API pack: ") + context.pathParam("id"));
    }
  };
}
