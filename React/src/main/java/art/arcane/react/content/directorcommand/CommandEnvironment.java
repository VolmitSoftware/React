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

package art.arcane.react.content.directorcommand;

import art.arcane.react.React;
import art.arcane.react.api.benchmark.Hastebin;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.EnvironmentMessages;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.project.hardware.getHardware;
import art.arcane.react.util.reflect.Platform;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;
import oshi.SystemInfo;

@Director(
    name = "environment",
    aliases = {"env"},
    origin = DirectorOrigin.BOTH,
    description = "Inspect system and server environment information",
    descriptionKey = "command.description.environment"
)
public class CommandEnvironment implements DirectorExecutor {

  @Director(
      name = "info",
      aliases = {"i"},
      description = "Print environment details",
      descriptionKey = "command.description.environment.info"
  )
  public void info() {
    SystemInfo systemInfo = new SystemInfo();
    KList<String> disks = new KList<>(getHardware.getDisk());
    KList<String> interfaces = new KList<>(getHardware.getInterfaces());
    KList<String> displays = new KList<>(getHardware.getEDID());
    KList<String> sensors = new KList<>(getHardware.getSensors());
    KList<String> gpus = new KList<>(getHardware.getGraphicsCards());
    KList<String> powersources = new KList<>(getHardware.getPowerSources());

    ReactLanguage.send(sender(), EnvironmentMessages.REACT_HEADER);
    ReactLanguage.send(sender(), EnvironmentMessages.REACT_VERSION, MessageArgument.untrusted("version", React.instance.getDescription().getVersion()));
    ReactLanguage.send(sender(), EnvironmentMessages.SERVER_TYPE, MessageArgument.untrusted("server", Bukkit.getVersion()));
    ReactLanguage.send(sender(), EnvironmentMessages.SERVER_UPTIME, MessageArgument.untrusted("uptime", Form.stampTime(systemInfo.getOperatingSystem().getSystemUptime())));
    ReactLanguage.send(sender(), EnvironmentMessages.PLATFORM_HEADER);
    ReactLanguage.send(
        sender(),
        EnvironmentMessages.PLATFORM,
        MessageArgument.untrusted("version", Platform.getVersion()),
        MessageArgument.untrusted("platform", Platform.getName())
    );
    ReactLanguage.send(
        sender(),
        EnvironmentMessages.JAVA,
        MessageArgument.untrusted("vendor", Platform.ENVIRONMENT.getJavaVendor()),
        MessageArgument.untrusted("version", Platform.ENVIRONMENT.getJavaVersion())
    );
    ReactLanguage.send(sender(), EnvironmentMessages.PROCESSOR_HEADER);
    ReactLanguage.send(sender(), EnvironmentMessages.CPU_MODEL, MessageArgument.untrusted("model", getHardware.getCPUModel()));
    ReactLanguage.send(
        sender(),
        EnvironmentMessages.CPU_ARCHITECTURE,
        MessageArgument.untrusted("architecture", Platform.CPU.getArchitecture()),
        MessageArgument.untrusted("processors", Platform.CPU.getAvailableProcessors())
    );
    ReactLanguage.send(
        sender(),
        EnvironmentMessages.CPU_LOAD,
        MessageArgument.untrusted("system_load", Form.pc(Platform.CPU.getCPULoad())),
        MessageArgument.untrusted("process_load", Form.pc(Platform.CPU.getLiveProcessCPULoad()))
    );
    ReactLanguage.send(sender(), EnvironmentMessages.GRAPHICS_HEADER);
    for (String gpu : gpus) {
      ReactLanguage.send(sender(), EnvironmentMessages.GRAPHICS_ENTRY, MessageArgument.untrusted("value", gpu));
    }
    ReactLanguage.send(sender(), EnvironmentMessages.MEMORY_HEADER);
    ReactLanguage.send(
        sender(),
        EnvironmentMessages.MEMORY_PHYSICAL,
        MessageArgument.untrusted("total", Form.memSize(Platform.MEMORY.PHYSICAL.getTotalMemory())),
        MessageArgument.untrusted("free", Form.memSize(Platform.MEMORY.PHYSICAL.getFreeMemory())),
        MessageArgument.untrusted("used", Form.memSize(Platform.MEMORY.PHYSICAL.getUsedMemory()))
    );
    ReactLanguage.send(
        sender(),
        EnvironmentMessages.MEMORY_VIRTUAL,
        MessageArgument.untrusted("total", Form.memSize(Platform.MEMORY.VIRTUAL.getTotalMemory())),
        MessageArgument.untrusted("free", Form.memSize(Platform.MEMORY.VIRTUAL.getFreeMemory())),
        MessageArgument.untrusted("used", Form.memSize(Platform.MEMORY.VIRTUAL.getUsedMemory()))
    );
    ReactLanguage.send(sender(), EnvironmentMessages.STORAGE_HEADER);
    for (String disk : disks) {
      ReactLanguage.send(sender(), EnvironmentMessages.STORAGE_ENTRY, MessageArgument.untrusted("value", disk));
    }
    ReactLanguage.send(sender(), EnvironmentMessages.INTERFACE_HEADER);
    for (String inter : interfaces) {
      ReactLanguage.send(sender(), EnvironmentMessages.INTERFACE_ENTRY, MessageArgument.untrusted("value", inter));
    }
    ReactLanguage.send(sender(), EnvironmentMessages.DISPLAY_HEADER);
    for (String display : displays) {
      ReactLanguage.send(sender(), EnvironmentMessages.DISPLAY_ENTRY, MessageArgument.untrusted("value", display));
    }
    ReactLanguage.send(sender(), EnvironmentMessages.SENSOR_HEADER);
    for (String sensor : sensors) {
      ReactLanguage.send(sender(), EnvironmentMessages.SENSOR_ENTRY, MessageArgument.untrusted("value", sensor));
    }
    ReactLanguage.send(sender(), EnvironmentMessages.POWER_HEADER);
    for (String power : powersources) {
      ReactLanguage.send(sender(), EnvironmentMessages.POWER_ENTRY, MessageArgument.untrusted("value", power));
    }
    Hastebin.enviornment(sender());

  }
}
