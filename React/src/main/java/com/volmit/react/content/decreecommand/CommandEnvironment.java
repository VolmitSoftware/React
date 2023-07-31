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
