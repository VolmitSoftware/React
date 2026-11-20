/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.content.tweak;

import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ShorthandHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Configuration for Shorthands. Adds individually configurable operator command shortcuts without changing /reload.")
public final class TweakShorthands extends ReactTweak {
  public static final String ID = "shorthands";

  @Getter
  @ConfigDoc(value = "Registers /gms for switching the caster to Survival mode.", impact = "Disable to leave /gms unregistered while the Shorthands tweak is active.")
  private boolean gms = true;
  @Getter
  @ConfigDoc(value = "Registers /gmsp for switching the caster to Spectator mode.", impact = "Disable to leave /gmsp unregistered while the Shorthands tweak is active.")
  private boolean gmsp = true;
  @Getter
  @ConfigDoc(value = "Registers /gmc for switching the caster to Creative mode.", impact = "Disable to leave /gmc unregistered while the Shorthands tweak is active.")
  private boolean gmc = true;
  @Getter
  @ConfigDoc(value = "Registers /give <item> <amount> with Minecraft item tab completion.", impact = "While enabled, the shorthand owns bare /give; namespaced vanilla give remains available.")
  private boolean give = true;
  @Getter
  @ConfigDoc(value = "Registers /more for granting one maximum-size copy of the exact held item.", impact = "Disable to leave /more unregistered while the Shorthands tweak is active.")
  private boolean more = true;
  @Getter
  @ConfigDoc(value = "Registers /rl as an alias that invokes the server's bare /reload command.", impact = "Disable to leave /rl unregistered while the Shorthands tweak is active.")
  private boolean rl = true;
  @Getter
  @ConfigDoc(
      value = "Custom shorthand commands keyed by their command label. Add a [customCommands.day] table with enabled = true, command = \"time set day\", permission = \"react.shorthands.custom\", and overrideExisting = false. Arguments typed after the shorthand are appended to the configured command; put the word args inside braces in the command to place them elsewhere.",
      impact = "Each enabled entry dynamically adds one command. The target command still enforces its own permission, and existing command labels are protected unless overrideExisting is enabled."
  )
  private Map<String, CustomShorthand> customCommands = new LinkedHashMap<>();

  private transient final AtomicLong lifecycleGeneration = new AtomicLong();
  private transient volatile Subscription commandService;

  public TweakShorthands() {
    super(ID);
    setEnabled(false);
  }

  @Override
  public void onActivate() {
    long generation = lifecycleGeneration.incrementAndGet();
    runOnCommandThread(() -> activate(generation));
  }

  @Override
  public void onDeactivate() {
    long generation = lifecycleGeneration.incrementAndGet();
    runOnCommandThread(() -> deactivate(generation));
  }

  private void activate(long generation) {
    if (generation != lifecycleGeneration.get()) {
      return;
    }

    Subscription activeService = commandService;
    commandService = null;
    if (activeService != null) {
      unregister(activeService);
    }

    try {
      Subscription nextService = host().register(this);
      if (generation != lifecycleGeneration.get()) {
        unregister(nextService);
        return;
      }
      commandService = nextService;
    } catch (Throwable throwable) {
      ReactComponents.reportError("Failed to register shorthand commands: " + throwable.getMessage(), throwable);
    }
  }

  private void deactivate(long generation) {
    if (generation != lifecycleGeneration.get()) {
      return;
    }

    Subscription activeService = commandService;
    commandService = null;
    if (activeService != null) {
      unregister(activeService);
    }
  }

  private void unregister(Subscription service) {
    try {
      service.cancel();
    } catch (Throwable throwable) {
      ReactComponents.reportError("Failed to unregister shorthand commands: " + throwable.getMessage(), throwable);
    }
  }

  private void runOnCommandThread(Runnable operation) {
    if (!host().runOnCommandThread(operation)) {
      ReactPlatforms.get().warn("Failed to schedule shorthand command-map update on the global server thread.");
    }
  }

  private static ShorthandHost host() {
    return ReactPlatforms.get().shorthandHost().orElseThrow(() -> new IllegalStateException("No shorthand host on this platform"));
  }

  @Getter
  public static final class CustomShorthand {
    @ConfigDoc(value = "Enables this custom shorthand entry.", impact = "Disable to keep the definition in config without registering its command.")
    private boolean enabled = true;
    @ConfigDoc(value = "Command dispatched as the shorthand user, without the leading slash. Supplied arguments append automatically unless the word args appears inside braces.", impact = "The target command runs with the invoking sender's existing permissions and cannot elevate access.")
    private String command = "";
    @ConfigDoc(value = "Optional permission required to use this shorthand in addition to the target command's permission. Leave blank for no extra shorthand permission.", impact = "The default keeps custom shorthands operator-only through react.shorthands.custom.")
    private String permission = "react.shorthands.custom";
    @ConfigDoc(value = "Allows this shorthand to temporarily replace an existing bare command label while active.", impact = "Keep false to skip conflicting labels safely. React restores any replaced mapping when the shorthand is disabled or reloaded.")
    private boolean overrideExisting = false;
  }
}
