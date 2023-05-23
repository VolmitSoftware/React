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
        aliases = {"env", "e"},
        origin = DecreeOrigin.BOTH,
        description = "The benchmark command"
)
public class CommandEnvironment implements DecreeExecutor {

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
