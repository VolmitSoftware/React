package art.arcane.react.core.gui;

import art.arcane.react.React;
import art.arcane.react.core.controller.ConfigInputController;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.HotloadController;
import art.arcane.react.core.controller.PlayerController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.platform.bukkit.BukkitLanguage;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.react.util.project.registry.ItemIcons;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

final class BukkitSettingsSource implements SettingsSource {
  static boolean canConfigure(Player player) {
    return player.isOp() || player.hasPermission("react.configurator");
  }

  @Override
  public boolean canConfigure(UUID viewerId) {
    Player player = Bukkit.getPlayer(viewerId);
    return player != null && canConfigure(player);
  }

  @Override
  public Object mainSettings() {
    return ReactConfiguration.get();
  }

  @Override
  public File mainFile() {
    return React.instance.getDataFile("react.toml");
  }

  @Override
  public boolean reloadMain() {
    boolean loaded = ReactConfiguration.reload();
    if (!loaded || !ReactLanguage.reload()) {
      return false;
    }
    React.instance.refreshRuntimeSettings(ReactConfiguration.get());
    J.s(() -> {
      FeatureController featureController = React.controller(FeatureController.class);
      if (featureController != null) {
        featureController.reconcileRuntimeMode();
      }
      EntityController entityController = React.controller(EntityController.class);
      if (entityController != null) {
        ReactConfiguration.get().getPriority().rebuildPriority();
      }
      PlayerController playerController = React.controller(PlayerController.class);
      if (playerController != null) {
        playerController.updateMonitors();
      }
    });
    return true;
  }

  @Override
  public List<CoreSection> coreSections() {
    List<CoreSection> sections = new ArrayList<>();
    if (React.instance == null || React.instance.getControllerRegistry() == null) {
      return sections;
    }
    for (IController controller : React.instance.getControllerRegistry().all()) {
      if (controller != null) {
        sections.add(new CoreSection(
            controller.getId(),
            controller.getName(),
            ItemIcons.material(controller.getIcon()).name().toLowerCase(Locale.ROOT),
            controller
        ));
      }
    }
    return sections;
  }

  @Override
  public boolean reloadCore(String id) {
    IController controller = id == null || id.isBlank() || React.instance == null || React.instance.getControllerRegistry() == null
        ? null
        : React.instance.getControllerRegistry().get(id);
    if (controller == null) {
      return false;
    }
    controller.loadConfiguration();
    if (controller instanceof HotloadController hotloadController) {
      hotloadController.refreshAfterConfigReload();
    }
    if (controller instanceof PlayerController playerController) {
      playerController.updateMonitors();
    }
    return true;
  }

  @Override
  public File dataFile(String category, String fileName) {
    return React.instance.getDataFile(category, fileName);
  }

  @Override
  public SampleController samples() {
    return React.controller(SampleController.class);
  }

  @Override
  public void afterSamplerReload() {
    PlayerController playerController = React.controller(PlayerController.class);
    if (playerController != null) {
      playerController.updateMonitors();
    }
  }

  @Override
  public void writeConfig(File file, String content) throws IOException {
    ConfigFileSupport.writeConfig(file, content);
  }

  @Override
  public void openServerLanguage(UUID viewerId) {
    Player player = Bukkit.getPlayer(viewerId);
    if (player != null) {
      BukkitLanguage.switcher().command(player, new String[]{"server"});
    }
  }

  @Override
  public Optional<SettingsInputSessions> inputSessions() {
    ConfigInputController controller = React.controller(ConfigInputController.class);
    return controller == null ? Optional.empty() : Optional.of(controller.sessions());
  }
}
