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

import java.nio.ByteBuffer;
import java.util.TreeMap;

public class MemoryBenchmark implements Runnable {
    private CommandSender sender;

    public MemoryBenchmark(CommandSender sender) {
        this.sender = sender;
    }

    public void benchmarkMemory() {
        J.a(this);
    }

    @Override
    public void run() {
        int score = doMemoryBenchmark();
        String result = MemoryResult.getSpeedLabel(score);
        sender.sendMessage(ChatColor.GREEN + "Benchmark result: " + result + " (" + score + ")");
    }

    private int doMemoryBenchmark() {
        sender.sendMessage(ChatColor.DARK_RED + "Benchmarking memory...");

        long startTime = System.nanoTime();

        int bufferSize = 1024 * 1024; // 1MB buffer size
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

        for (int i = 0; i < bufferSize; i++) {
            buffer.put((byte) (i % 256));
        }

        long endTime = System.nanoTime();
        long duration = (endTime - startTime);

        sender.sendMessage(ChatColor.YELLOW + "Benchmark complete.");
        return (int) (1000000000.0 / (duration / (double) bufferSize));
    }

    private enum MemoryResult {
        ULTRA_SLOW("Ultra Slow!"), VERY_SLOW("Very Slow!"), SLOW("Slow!"), AVERAGE("Average."),
        GOOD("Good!"), FAST("Fast!"), VERY_FAST("Very Fast!"), ULTRA_FAST("Ultra Fast"),
        INSANELY_FAST("Insanely Fast!");

        private final String label;

        MemoryResult(String label) {
            this.label = label;
        }

        public static String getSpeedLabel(int speed) {
            TreeMap<Integer, MemoryResult> speedMap = new TreeMap<>();
            speedMap.put(500, INSANELY_FAST);
            speedMap.put(400, ULTRA_FAST);
            speedMap.put(300, VERY_FAST);
            speedMap.put(200, FAST);
            speedMap.put(150, GOOD);
            speedMap.put(100, AVERAGE);
            speedMap.put(75, SLOW);
            speedMap.put(50, VERY_SLOW);

            for (int speedThreshold : speedMap.descendingKeySet()) {
                if (speed > speedThreshold) {
                    return speedMap.get(speedThreshold).label;
                }
            }

            return ULTRA_SLOW.label;
        }
    }
}
