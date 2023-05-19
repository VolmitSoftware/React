package com.volmit.react.content.command;

import com.volmit.react.React;
import com.volmit.react.api.benchmark.CPUBenchmark;
import com.volmit.react.api.benchmark.DriveBenchmark;
import com.volmit.react.api.benchmark.Hastebin;
import com.volmit.react.api.benchmark.MemoryBenchmark;
import com.volmit.react.util.decree.DecreeExecutor;
import com.volmit.react.util.decree.DecreeOrigin;
import com.volmit.react.util.decree.annotations.Decree;
import com.volmit.react.util.format.C;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.reflect.Platform;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Decree(
        name = "environment",
        aliases = {"env", "info"},
        origin = DecreeOrigin.BOTH,
        description = "The benchmark command"
)
public class CommandEnvironment implements DecreeExecutor {
    @Decree(
            name = "environment-info",
            aliases = {"info", "i"},
            description = "Print the environment details!"
    )
    public void enviornment() {
        sender().sendMessage(C.BOLD + "" + C.DARK_AQUA + " -- == React Info == -- ");
        sender().sendMessage(C.GOLD + "React Version Version: " + React.instance.getDescription().getVersion());
        sender().sendMessage(C.GOLD + "Server Type: " + Bukkit.getVersion());
        sender().sendMessage(C.BOLD + "" + C.DARK_AQUA + " -- == Platform Overview == -- ");
        sender().sendMessage(C.GOLD + "Version: " + Platform.getVersion() + " - Platform: " + Platform.getName());
        sender().sendMessage(C.GOLD + "Java Vendor: " + Platform.ENVIRONMENT.getJavaVendor() + " - Java Version: " + Platform.ENVIRONMENT.getJavaVersion());
        sender().sendMessage(C.BOLD + "" + C.DARK_AQUA + " -- == Storage Information == -- ");
        sender().sendMessage(C.GOLD + "Total Space: " + Form.memSize(Platform.STORAGE.getTotalSpace()));
        sender().sendMessage(C.GOLD + "Free Space: " + Form.memSize(Platform.STORAGE.getFreeSpace()));
        sender().sendMessage(C.GOLD + "Used Space: " + Form.memSize(Platform.STORAGE.getUsedSpace()));
        sender().sendMessage(C.BOLD + "" + C.DARK_AQUA + " -- == Memory Information == -- ");
        sender().sendMessage(C.GOLD + "Physical Memory - Total: " + Form.memSize(Platform.MEMORY.PHYSICAL.getTotalMemory()) + " Free: " + Form.memSize(Platform.MEMORY.PHYSICAL.getFreeMemory()) + " Used: " + Form.memSize(Platform.MEMORY.PHYSICAL.getUsedMemory()));
        sender().sendMessage(C.GOLD + "Virtual Memory - Total: " + Form.memSize(Platform.MEMORY.VIRTUAL.getTotalMemory()) + " Free: " + Form.memSize(Platform.MEMORY.VIRTUAL.getFreeMemory()) + " Used: " + Form.memSize(Platform.MEMORY.VIRTUAL.getUsedMemory()));
        sender().sendMessage(C.BOLD + "" + C.DARK_AQUA + " -- == CPU Overview == -- ");
        sender().sendMessage(C.GOLD + "CPU Architecture: " + Platform.CPU.getArchitecture() + " Available Processors: " + Platform.CPU.getAvailableProcessors());
        sender().sendMessage(C.GOLD + "CPU Load: " + Form.pc(Platform.CPU.getCPULoad()) + " CPU Live Process Load: " + Form.pc(Platform.CPU.getLiveProcessCPULoad()));
        Hastebin.enviornment(sender());

    }



    @Decree(
            name = "cpu-benchmark",
            aliases = {"cpu"},
            description = "Benchmark the CPU"
    )
    public void cpuBenchmark() {
        new CPUBenchmark(sender()).run();
    }


    @Decree(
            name = "drive-benchmark",
            aliases = {"drive"},
            description = "Benchmark the Hard-Drive"
    )
    public void driveBenchmark() {
        new DriveBenchmark(sender()).run();
    }


    @Decree(
            name = "memory-benchmark",
            aliases = {"mem"},
            description = "Benchmark the Memory"
    )
    public void memoryBenchmark() {
        new MemoryBenchmark(sender()).run();
    }

}
