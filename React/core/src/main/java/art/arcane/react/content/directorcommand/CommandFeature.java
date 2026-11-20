package art.arcane.react.content.directorcommand;

import art.arcane.react.api.feature.Feature;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.platform.CommandTargets;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.director.handlers.FeatureHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

@Director(
    name = "feature",
    aliases = {"features", "feat"},
    origin = DirectorOrigin.BOTH,
    description = "List, enable and disable React features",
    descriptionKey = "command.description.feature"
)
public class CommandFeature implements DirectorExecutor {
  @Director(
      name = "list",
      aliases = {"ls"},
      description = "List every feature and whether it is running",
      descriptionKey = "command.description.feature.list"
  )
  public void list() {
    FeatureController controller = ReactComponents.features();
    ComponentCommands.list(sender(), GuiMessages.CONFIG_FEATURE_COUNT,
        controller == null || controller.getFeatures() == null ? null : controller.getFeatures().all(),
        controller == null || controller.getActiveFeatures() == null ? null : controller.getActiveFeatures().keySet(),
        Feature::isEnabled);
  }

  @Director(
      name = "enable",
      aliases = {"on"},
      description = "Enable a feature and save it to its config",
      descriptionKey = "command.description.feature.enable"
  )
  public void enable(
      @Param(
          name = "feature",
          description = "The feature to change",
          descriptionKey = "command.parameter.feature",
          customHandler = FeatureHandler.class
      ) CommandTargets.Feature feature
  ) {
    ComponentCommands.set(sender(), "feature", ReactComponents.feature(feature.id()), Feature::isEnabled, true);
  }

  @Director(
      name = "disable",
      aliases = {"off"},
      description = "Disable a feature and save it to its config",
      descriptionKey = "command.description.feature.disable"
  )
  public void disable(
      @Param(
          name = "feature",
          description = "The feature to change",
          descriptionKey = "command.parameter.feature",
          customHandler = FeatureHandler.class
      ) CommandTargets.Feature feature
  ) {
    ComponentCommands.set(sender(), "feature", ReactComponents.feature(feature.id()), Feature::isEnabled, false);
  }
}
