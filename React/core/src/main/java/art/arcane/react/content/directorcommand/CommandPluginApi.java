package art.arcane.react.content.directorcommand;

import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;

@Director(
    name = "plugin-api",
    aliases = {"packs"},
    origin = DirectorOrigin.BOTH,
    description = "Manage community Plugin API metric packs",
    descriptionKey = "command.description.plugin_api"
)
public final class CommandPluginApi implements DirectorExecutor {
  @Director(
      name = "status",
      aliases = {"list"},
      origin = DirectorOrigin.BOTH,
      description = "Show loaded Plugin API packs and validation errors",
      descriptionKey = "command.description.plugin_api.status"
  )
  public void status() {
    host().pluginApiStatus(sender());
  }

  @Director(
      name = "reload",
      aliases = {"rl"},
      origin = DirectorOrigin.BOTH,
      description = "Rescan the Plugin API pack folder",
      descriptionKey = "command.description.plugin_api.reload"
  )
  public void reload() {
    host().pluginApiReload(sender());
  }
}
