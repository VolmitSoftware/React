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

import art.arcane.edict.api.context.EdictContext;
import com.volmit.react.React;
import com.volmit.react.api.benchmark.Hastebin;
import com.volmit.react.util.collection.KList;
import com.volmit.react.util.decree.DecreeExecutor;
import com.volmit.react.util.decree.DecreeOrigin;
import com.volmit.react.util.decree.annotations.Decree;
import com.volmit.react.util.format.C;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.hardware.getHardware;
import com.volmit.react.util.reflect.Platform;
import org.bukkit.Bukkit;
import oshi.SystemInfo;

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
        SystemInfo systemInfo = new SystemInfo();
        KList<String> disks = new KList<>(getHardware.getDisk());
        KList<String> interfaces = new KList<>(getHardware.getInterfaces());
        KList<String> displays = new KList<>(getHardware.getEDID());
        KList<String> sensors = new KList<>(getHardware.getSensors());
        KList<String> gpus = new KList<>(getHardware.getGraphicsCards());
        KList<String> powersources = new KList<>(getHardware.getPowerSources());

        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == React Info == -- ");
        sender().sendMessage(C.AQUA + "React Version Version: " + React.instance.getDescription().getVersion());
        sender().sendMessage(C.AQUA + "Server Type: " + Bukkit.getVersion());
        sender().sendMessage(C.AQUA + "Server Uptime: " + Form.stampTime(systemInfo.getOperatingSystem().getSystemUptime()));
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Platform Overview == -- ");
        sender().sendMessage(C.AQUA + "Version: " + Platform.getVersion() + " - Platform: " + Platform.getName());
        sender().sendMessage(C.AQUA + "Java Vendor: " + Platform.ENVIRONMENT.getJavaVendor() + " - Java Version: " + Platform.ENVIRONMENT.getJavaVersion());
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Processor Overview == -- ");
        sender().sendMessage(C.AQUA + "CPU Model: " + getHardware.getCPUModel());
        sender().sendMessage(C.AQUA + "CPU Architecture: " + Platform.CPU.getArchitecture() + " Available Processors: " + Platform.CPU.getAvailableProcessors());
        sender().sendMessage(C.AQUA + "CPU Load: " + Form.pc(Platform.CPU.getCPULoad()) + " CPU Live Process Load: " + Form.pc(Platform.CPU.getLiveProcessCPULoad()));
        sender().sendMessage(C.DARK_GRAY + "-=" + C.BLUE +" Graphics " + C.DARK_GRAY + "=- ");
        for (String gpu : gpus) {
            sender().sendMessage(C.BLUE + " " + gpu);
        }
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Memory Information == -- ");
        sender().sendMessage(C.AQUA + "Physical Memory - Total: " + Form.memSize(Platform.MEMORY.PHYSICAL.getTotalMemory()) + " Free: " + Form.memSize(Platform.MEMORY.PHYSICAL.getFreeMemory()) + " Used: " + Form.memSize(Platform.MEMORY.PHYSICAL.getUsedMemory()));
        sender().sendMessage(C.AQUA + "Virtual Memory - Total: " + Form.memSize(Platform.MEMORY.VIRTUAL.getTotalMemory()) + " Free: " + Form.memSize(Platform.MEMORY.VIRTUAL.getFreeMemory()) + " Used: " + Form.memSize(Platform.MEMORY.VIRTUAL.getUsedMemory()));
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Storage Information == -- ");
        for (String disk : disks) {
            sender().sendMessage(C.AQUA + " " + disk);
        }
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Interface Information == -- ");
        for (String inter : interfaces) {
            sender().sendMessage(C.AQUA + " " + inter);
        }
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Display Information == -- ");
        for (String display : displays) {
            sender().sendMessage(C.AQUA + " " + display);
        }
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Sensor Information == -- ");
        for (String sensor : sensors) {
            sender().sendMessage(C.AQUA + " " + sensor);
        }
        sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Power Information == -- ");
        for (String power : powersources) {
            sender().sendMessage(C.AQUA + " " + power);
        }
        Hastebin.enviornment(sender());

    }
}
