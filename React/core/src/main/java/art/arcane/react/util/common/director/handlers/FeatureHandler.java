package art.arcane.react.util.director.handlers;

import art.arcane.react.api.feature.Feature;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.platform.CommandTargets;
import art.arcane.react.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class FeatureHandler implements DirectorParameterHandler<CommandTargets.Feature> {
  @Override
  public KList<CommandTargets.Feature> getPossibilities() {
    KList<CommandTargets.Feature> options = new KList<>();
    FeatureController controller = ReactComponents.features();
    if (controller != null && controller.getFeatures() != null) {
      for (Feature feature : controller.getFeatures().all()) {
        options.add(new CommandTargets.Feature(feature.getId()));
      }
    }
    return options;
  }

  @Override
  public String toString(CommandTargets.Feature feature) {
    return feature.id();
  }

  @Override
  public CommandTargets.Feature parse(String in, boolean force) throws DirectorParsingException {
    for (CommandTargets.Feature option : getPossibilities()) {
      if (option.id().equalsIgnoreCase(in)) {
        return option;
      }
    }
    throw new DirectorParsingException("Unable to find Feature \"" + in + "\"");
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(CommandTargets.Feature.class);
  }

  @Override
  public String getRandomDefault() {
    return "entity-trimmer";
  }
}
