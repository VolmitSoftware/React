package art.arcane.react.content.directorcommand;

import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.director.HostCommand;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

@Director(
    name = "test",
    aliases = {"selftest"},
    origin = DirectorOrigin.BOTH,
    description = "Run React validation and load tests",
    descriptionKey = "command.description.test"
)
public class CommandTest implements DirectorExecutor {
  @Director(
      name = "run",
      aliases = {"r"},
      origin = DirectorOrigin.BOTH,
      sync = true,
      description = "Run the React validation suite",
      descriptionKey = "command.description.test.run"
  )
  @HostCommand("testRun")
  public void run(
      @Param(name = "full", description = "Run the full deep test suite", descriptionKey = "command.parameter.test.full", defaultValue = "true", aliases = {"f"})
      boolean full,
      @Param(name = "json", description = "Write a JSON report file", descriptionKey = "command.parameter.test.json", defaultValue = "true", aliases = {"j"})
      boolean json
  ) {
    host().testRun(sender(), full, json);
  }

  @Director(
      name = "loadtest",
      aliases = {"load"},
      origin = DirectorOrigin.BOTH,
      sync = true,
      description = "Run a two-pass synthetic load test",
      descriptionKey = "command.description.test.load"
  )
  @HostCommand("testLoad")
  public void loadtest(
      @Param(name = "confirm", description = "Must be true; this spawns heavy synthetic load on world 0", descriptionKey = "command.parameter.test.confirm")
      boolean confirm,
      @Param(name = "players", description = "Synthetic player count", descriptionKey = "command.parameter.test.players", defaultValue = "1000", aliases = {"p"})
      int players,
      @Param(name = "duration", description = "Seconds per pass, first with React on and then with React off", descriptionKey = "command.parameter.test.duration", defaultValue = "600", aliases = {"d"})
      int duration
  ) {
    host().testLoad(sender(), confirm, players, duration);
  }
}
