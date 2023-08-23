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

package com.volmit.react.content.decreecommand;

import com.volmit.react.React;
import com.volmit.react.api.benchmark.Hastebin;
import com.volmit.react.util.decree.DecreeExecutor;
import com.volmit.react.util.decree.DecreeOrigin;
import com.volmit.react.util.decree.annotations.Decree;
import com.volmit.react.util.format.C;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.reflect.Platform;
import org.bukkit.Bukkit;

@Decree(
        name = "environment",
        aliases = {"env"},
        origin = DecreeOrigin.BOTH,
        description = "This is the place to benchmark your system and get information about your system."
)
public class CommandEnvironment implements DecreeExecutor {

    @Decree(
            name = "info",
            aliases = {"i"},
            description = "Print the environment details!"
    )
    public void info() {
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == React Info == -- ");
        sender().sendMessage(C.GOLD + "React Version Version: " + React.instance.getDescription().getVersion());
        sender().sendMessage(C.GOLD + "Server Type: " + Bukkit.getVersion());
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Platform Overview == -- ");
        sender().sendMessage(C.GOLD + "Version: " + Platform.getVersion() + " - Platform: " + Platform.getName());
        sender().sendMessage(C.GOLD + "Java Vendor: " + Platform.ENVIRONMENT.getJavaVendor() + " - Java Version: " + Platform.ENVIRONMENT.getJavaVersion());
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Storage Information == -- ");
        sender().sendMessage(C.GOLD + "Total Space: " + Form.memSize(Platform.STORAGE.getTotalSpace()));
        sender().sendMessage(C.GOLD + "Free Space: " + Form.memSize(Platform.STORAGE.getFreeSpace()));
        sender().sendMessage(C.GOLD + "Used Space: " + Form.memSize(Platform.STORAGE.getUsedSpace()));
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Memory Information == -- ");
        sender().sendMessage(C.GOLD + "Physical Memory - Total: " + Form.memSize(Platform.MEMORY.PHYSICAL.getTotalMemory()) + " Free: " + Form.memSize(Platform.MEMORY.PHYSICAL.getFreeMemory()) + " Used: " + Form.memSize(Platform.MEMORY.PHYSICAL.getUsedMemory()));
        sender().sendMessage(C.GOLD + "Virtual Memory - Total: " + Form.memSize(Platform.MEMORY.VIRTUAL.getTotalMemory()) + " Free: " + Form.memSize(Platform.MEMORY.VIRTUAL.getFreeMemory()) + " Used: " + Form.memSize(Platform.MEMORY.VIRTUAL.getUsedMemory()));
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == CPU Overview == -- ");
        sender().sendMessage(C.GOLD + "CPU Architecture: " + Platform.CPU.getArchitecture() + " Available Processors: " + Platform.CPU.getAvailableProcessors());
        sender().sendMessage(C.GOLD + "CPU Load: " + Form.pc(Platform.CPU.getCPULoad()) + " CPU Live Process Load: " + Form.pc(Platform.CPU.getLiveProcessCPULoad()));
        Hastebin.enviornment(sender());

    }

}
