package art.arcane.react.localization;

import art.arcane.react.localization.catalog.ActionMessages;
import art.arcane.react.localization.catalog.BenchmarkMessages;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.localization.catalog.ConfigMessages;
import art.arcane.react.localization.catalog.DevMessages;
import art.arcane.react.localization.catalog.EnvironmentMessages;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.localization.catalog.ShorthandMessages;
import art.arcane.react.localization.catalog.TaxonomyMessages;
import art.arcane.react.localization.catalog.TestMessages;
import art.arcane.volmlib.util.director.DirectorMessages;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.react.util.project.config.ConfigLocalization;

public final class ReactMessages {
  private static final MessageCatalog CATALOG = createCatalog();

  private ReactMessages() {
  }

  public static MessageCatalog catalog() {
    return CATALOG;
  }

  public static MessageKey require(String id) {
    return CATALOG.require(id);
  }

  private static MessageCatalog createCatalog() {
    MessageCatalog.Builder builder = MessageCatalog.builder("en_US");
    builder.addAll(DirectorMessages.keys());
    ActionMessages.addTo(builder);
    BenchmarkMessages.addTo(builder);
    CommandMessages.addTo(builder);
    ConfigMessages.addTo(builder);
    ConfigLocalization.addTo(builder);
    DevMessages.addTo(builder);
    EnvironmentMessages.addTo(builder);
    GuiMessages.addTo(builder);
    MapMessages.addTo(builder);
    RendererMessages.addTo(builder);
    RuntimeMessages.addTo(builder);
    ShorthandMessages.addTo(builder);
    TaxonomyMessages.addTo(builder);
    TestMessages.addTo(builder);
    return builder.build();
  }
}
