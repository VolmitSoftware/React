package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.core.pluginapi.BukkitPluginApiHost;
import art.arcane.react.core.pluginapi.PluginApiPacks;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime.PackStatus;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class PluginApiPackController extends TickedObject implements IController {
  private final transient PluginApiPacks packs = new PluginApiPacks(new BukkitPluginApiHost());

  public PluginApiPackController() {
    super("react", "plugin-api-packs", 1_000L);
  }

  @Override
  public String getName() {
    return "Plugin API Packs";
  }

  @Override
  public void loadConfiguration() {
  }

  @Override
  public boolean reloadConfiguration() {
    return true;
  }

  @Override
  public Object prepareConfigurationSnapshot(File sourceFile, String rawContent) {
    return this;
  }

  @Override
  public boolean applyConfigurationSnapshot(Object loadedObject) {
    return loadedObject == this;
  }

  @Override
  public void start() {
    packs.start(React.instance.getDataFolder().toPath());
  }

  @Override
  public void postStart() {
    packs.requestReload();
  }

  @Override
  public void stop() {
    packs.stop();
  }

  @Override
  public void onTick() {
    packs.tick();
  }

  public void requestReload() {
    packs.requestReload();
  }

  public List<PackStatus> statuses() {
    return packs.statuses();
  }

  public PackStatus status(String id) {
    return packs.status(id);
  }

  public Map<String, String> validationErrors() {
    return packs.validationErrors();
  }

  public Path packFolder() {
    return packs.packFolder();
  }

  public PluginApiPacks.ValidationResult validate(String rawContent) {
    return packs.validate(rawContent);
  }

  public PackStatus install(String requestedId, String rawContent) throws IOException {
    return packs.install(requestedId, rawContent);
  }

  public boolean remove(String id) throws IOException {
    return packs.remove(id);
  }
}
