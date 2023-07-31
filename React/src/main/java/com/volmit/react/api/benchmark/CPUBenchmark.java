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

package com.volmit.react.api.benchmark;

import com.volmit.react.util.scheduling.J;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.TreeMap;

public class CPUBenchmark implements Runnable {
    private CommandSender sender;

    public CPUBenchmark(CommandSender sender) {
        this.sender = sender;
    }

    public void BenchmarkCPU() {
        J.a(this);
    }

    @Override
    public void run() {
        int score = doCPUBenchmark();
        String result = CPUResult.getSpeedLabel(score);
        sender.sendMessage(ChatColor.GREEN + "Benchmark result: " + result + " (" + score + ")");
    }

    private int doCPUBenchmark() {
        long startTime = System.nanoTime();
        sender.sendMessage(ChatColor.DARK_RED + "Benchmarking CPU...");

        double result = 0.01;
        for (int i = 0; i < 1000000; i++) {
            for (int j = 0; j < 100; j++) {
                result *= 1.000001;
            }
        }

        long endTime = System.nanoTime();
        long duration = (endTime - startTime);

        sender.sendMessage(ChatColor.YELLOW + "Benchmark complete.");
        return (((int) (1000000000.0 / (duration / 1000000.0))) / 10000);
    }

    private enum CPUResult {
        ULTRA_SLOW("Ultra Slow!"), VERY_SLOW("Very Slow!"), SLOW("Slow!"), AVERAGE("Average."),
        GOOD("Good!"), FAST("Fast!"), VERY_FAST("Very fast!"), ULTRA_FAST("Ultra Fast"), INSANELY_FAST("Insanely Fast!");

        private final String m;

        CPUResult(String m) {
            this.m = m;
        }

        public static String getSpeedLabel(int s) {
            TreeMap<Integer, CPUResult> speedMap = new TreeMap<>();
            speedMap.put(4000, INSANELY_FAST);
            speedMap.put(2000, ULTRA_FAST);
            speedMap.put(1500, VERY_FAST);
            speedMap.put(1000, FAST);
            speedMap.put(800, GOOD);
            speedMap.put(500, AVERAGE);
            speedMap.put(300, SLOW);
            speedMap.put(100, VERY_SLOW);

            for (int speedThreshold : speedMap.descendingKeySet()) {
                if (s > speedThreshold) {
                    return speedMap.get(speedThreshold).m;
                }
            }

            return ULTRA_SLOW.m;
        }
    }
}
