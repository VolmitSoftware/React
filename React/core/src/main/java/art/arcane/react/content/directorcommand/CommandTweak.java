package art.arcane.react.content.directorcommand;

import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.core.controller.TweakController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.platform.CommandTargets;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.director.handlers.TweakHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

@Director(
    name = "tweak",
    aliases = {"tweaks", "tw"},
    origin = DirectorOrigin.BOTH,
    description = "List, enable and disable React tweaks",
    descriptionKey = "command.description.tweak"
)
public class CommandTweak implements DirectorExecutor {
  @Director(
      name = "list",
      aliases = {"ls"},
      description = "List every tweak and whether it is running",
      descriptionKey = "command.description.tweak.list"
  )
  public void list() {
    TweakController controller = ReactComponents.tweaks();
    ComponentCommands.list(sender(), GuiMessages.CONFIG_TWEAK_COUNT,
        controller == null || controller.getTweaks() == null ? null : controller.getTweaks().all(),
        controller == null || controller.getActiveTweaks() == null ? null : controller.getActiveTweaks().keySet(),
        Tweak::isEnabled);
  }

  @Director(
      name = "enable",
      aliases = {"on"},
      description = "Enable a tweak and save it to its config",
      descriptionKey = "command.description.tweak.enable"
  )
  public void enable(
      @Param(
          name = "tweak",
          description = "The tweak to change",
          descriptionKey = "command.parameter.tweak",
          customHandler = TweakHandler.class
      ) CommandTargets.Tweak tweak
  ) {
    ComponentCommands.set(sender(), "tweak", ReactComponents.tweak(tweak.id()), Tweak::isEnabled, true);
  }

  @Director(
      name = "disable",
      aliases = {"off"},
      description = "Disable a tweak and save it to its config",
      descriptionKey = "command.description.tweak.disable"
  )
  public void disable(
      @Param(
          name = "tweak",
          description = "The tweak to change",
          descriptionKey = "command.parameter.tweak",
          customHandler = TweakHandler.class
      ) CommandTargets.Tweak tweak
  ) {
    ComponentCommands.set(sender(), "tweak", ReactComponents.tweak(tweak.id()), Tweak::isEnabled, false);
  }
}
