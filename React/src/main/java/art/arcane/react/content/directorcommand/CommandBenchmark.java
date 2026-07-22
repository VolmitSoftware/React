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

import art.arcane.react.api.benchmark.CPUBenchmark;
import art.arcane.react.api.benchmark.DriveBenchmark;
import art.arcane.react.api.benchmark.MemoryBenchmark;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;

@Director(
    name = "benchmark",
    aliases = {"bench"},
    origin = DirectorOrigin.BOTH,
    description = "Relative system benchmark commands",
    descriptionKey = "command.description.benchmark"
)
public class CommandBenchmark implements DirectorExecutor {

  @Director(
      name = "cpu-benchmark",
      aliases = {"cpu", "processor"},
      description = "Benchmark the CPU",
      descriptionKey = "command.description.benchmark.cpu"
  )
  public void cpuBenchmark() {
    new CPUBenchmark(sender()).run();
  }


  @Director(
      name = "drive-benchmark",
      aliases = {"drive"},
      description = "Benchmark the storage drive",
      descriptionKey = "command.description.benchmark.drive"
  )
  public void driveBenchmark() {
    new DriveBenchmark(sender()).run();
  }


  @Director(
      name = "memory-benchmark",
      aliases = {"mem", "memory", "ram"},
      description = "Benchmark memory",
      descriptionKey = "command.description.benchmark.memory"
  )
  public void memoryBenchmark() {
    new MemoryBenchmark(sender()).run();
  }


}
