package art.arcane.react.util.director.handlers;

import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.core.controller.TweakController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.platform.CommandTargets;
import art.arcane.react.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class TweakHandler implements DirectorParameterHandler<CommandTargets.Tweak> {
  @Override
  public KList<CommandTargets.Tweak> getPossibilities() {
    KList<CommandTargets.Tweak> options = new KList<>();
    TweakController controller = ReactComponents.tweaks();
    if (controller != null && controller.getTweaks() != null) {
      for (Tweak tweak : controller.getTweaks().all()) {
        options.add(new CommandTargets.Tweak(tweak.getId()));
      }
    }
    return options;
  }

  @Override
  public String toString(CommandTargets.Tweak tweak) {
    return tweak.id();
  }

  @Override
  public CommandTargets.Tweak parse(String in, boolean force) throws DirectorParsingException {
    for (CommandTargets.Tweak option : getPossibilities()) {
      if (option.id().equalsIgnoreCase(in)) {
        return option;
      }
    }
    throw new DirectorParsingException("Unable to find Tweak \"" + in + "\"");
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(CommandTargets.Tweak.class);
  }

  @Override
  public String getRandomDefault() {
    return "hopper-limit";
  }
}
