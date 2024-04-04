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

package com.volmit.react.content.command;

import art.arcane.edict.Edict;
import art.arcane.edict.api.context.EdictContext;
import com.volmit.react.React;
import com.volmit.react.api.benchmark.CPUBenchmark;
import com.volmit.react.api.benchmark.Hastebin;
import com.volmit.react.api.benchmark.MemoryBenchmark;
import com.volmit.react.util.collection.KList;
import com.volmit.react.util.format.C;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.hardware.getHardware;
import com.volmit.react.util.reflect.Platform;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.units.qual.K;
import oshi.SystemInfo;

//THIS IS A DUMMY CLASS FOR EDICT WHENEVER ITS DONE use @decree instead of @edict
public class CommandReact {
    @Edict.Command("/react version")
    @Edict.Aliases("/react v")
    public static void version() {
        EdictContext.get().getSender().sendMessage("React " + React.instance.getDescription().getVersion());
    }

    @Edict.Command("/react reload")
    @Edict.Aliases("/react rl")
    public static void reload() {
        EdictContext.get().getSender().sendMessage("Reloading React, Beware this breaks PAPI Placeholders");
        React.instance.reload();
        EdictContext.get().getSender().sendMessage("React v" + React.instance.getDescription().getVersion() + " Reloaded!");
    }

    @Edict.Command("/react benchmark cpu")
    @Edict.Aliases({"/react benchmark processor", "/react bench processor", "/react bench cpu"})
    public static void benchmarkCPU() {
        new CPUBenchmark(EdictContext.sender()).run();
    }

    @Edict.Command("/react benchmark memory")
    @Edict.Aliases({"/react benchmark ram", "/react bench ram", "/react bench memory"})
    public static void benchmarkMemory() {
        new MemoryBenchmark(EdictContext.sender()).run();
    }

    @Edict.Command("/react environment")
    @Edict.Aliases("/react env")
    public static void environment() {
        SystemInfo systemInfo = new SystemInfo();
        KList<String> disks = new KList<>(getHardware.getDisk());
        KList<String> interfaces = new KList<>(getHardware.getInterfaces());
        KList<String> displays = new KList<>(getHardware.getEDID());
        KList<String> powersources = new KList<>(getHardware.getPowerSources());


        EdictContext.sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == React Info == -- ");
        EdictContext.sender().sendMessage(C.AQUA + "- React Version Version: " + React.instance.getDescription().getVersion());
        EdictContext.sender().sendMessage(C.AQUA + "- Server Type: " + Bukkit.getVersion());
        EdictContext.sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Platform Overview == -- ");
        EdictContext.sender().sendMessage(C.AQUA + "- Version: " + Platform.getVersion() + " - Platform: " + Platform.getName());
        EdictContext.sender().sendMessage(C.AQUA + "- Java Vendor: " + Platform.ENVIRONMENT.getJavaVendor() + " - Java Version: " + Platform.ENVIRONMENT.getJavaVersion());
        EdictContext.sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Storage Information == -- ");
        for (String disk : disks) {
            EdictContext.sender().sendMessage(C.AQUA + "- " + disk);
        }
        EdictContext.sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Interface Information == -- ");
        for (String inter : interfaces) {
            EdictContext.sender().sendMessage(C.AQUA + "- " + inter);
        }
        EdictContext.sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Display Information == -- ");
        for (String display : displays) {
            EdictContext.sender().sendMessage(C.AQUA + "- " + display);
        }
        EdictContext.sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Power Information == -- ");
        for (String power : powersources) {
            EdictContext.sender().sendMessage(C.AQUA + "- " + power);
        }
        EdictContext.sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == Memory Information == -- ");
        EdictContext.sender().sendMessage(C.AQUA + "- Physical Memory - Total: " + Form.memSize(Platform.MEMORY.PHYSICAL.getTotalMemory()) + " Free: " + Form.memSize(Platform.MEMORY.PHYSICAL.getFreeMemory()) + " Used: " + Form.memSize(Platform.MEMORY.PHYSICAL.getUsedMemory()));
        EdictContext.sender().sendMessage(C.AQUA + "- Virtual Memory - Total: " + Form.memSize(Platform.MEMORY.VIRTUAL.getTotalMemory()) + " Free: " + Form.memSize(Platform.MEMORY.VIRTUAL.getFreeMemory()) + " Used: " + Form.memSize(Platform.MEMORY.VIRTUAL.getUsedMemory()));
        EdictContext.sender().sendMessage(String.valueOf(C.BOLD) + C.DARK_AQUA + " -- == CPU Overview == -- ");
        EdictContext.sender().sendMessage(C.AQUA + "- CPU Architecture: " + Platform.CPU.getArchitecture() + " Available Processors: " + Platform.CPU.getAvailableProcessors());
        EdictContext.sender().sendMessage(C.AQUA + "- CPU Load: " + Form.pc(Platform.CPU.getCPULoad()) + " CPU Live Process Load: " + Form.pc(Platform.CPU.getLiveProcessCPULoad()));

        CommandSender s = EdictContext.sender();
        J.a(() -> Hastebin.enviornment(s));
    }
}
