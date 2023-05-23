package com.volmit.react.content.decreecommand;

import com.volmit.react.api.benchmark.CPUBenchmark;
import com.volmit.react.api.benchmark.DriveBenchmark;
import com.volmit.react.api.benchmark.MemoryBenchmark;
import com.volmit.react.util.decree.DecreeExecutor;
import com.volmit.react.util.decree.DecreeOrigin;
import com.volmit.react.util.decree.annotations.Decree;

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
