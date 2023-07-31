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

import com.volmit.react.util.plugin.VolmitSender;
import com.volmit.react.util.scheduling.J;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.TreeMap;

public class DriveBenchmark implements Runnable {
    private VolmitSender sender;

    public DriveBenchmark(VolmitSender sender) {
        this.sender = sender;
    }

    public void benchmarkDrive() {
        J.a(this);
    }

    @Override
    public void run() {
        double score = doDriveBenchmark();
        String result = DriveResult.getSpeedLabel(score);
        sender.sendMessage(ChatColor.GREEN + "Benchmark result: " + result + " (" + score + ")");
    }

    private double doDriveBenchmark() {
        sender.sendMessage(ChatColor.DARK_RED + "Benchmarking Drive...");

        // Create a temporary file
        File file;
        try {
            file = File.createTempFile("drive_benchmark", ".tmp");
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Failed to create a temporary file for benchmarking.");
            return 0.0;
        }

        // Write random data to the temporary file
        Random random = new Random();
        byte[] buffer = new byte[1024];
        random.nextBytes(buffer);

        long startTime = System.nanoTime();

        // Perform read and write operations
        try {
            for (int i = 0; i < 1000; i++) {
                // Write data to the file
                org.apache.commons.io.FileUtils.writeByteArrayToFile(file, buffer);

                // Read data from the file
                org.apache.commons.io.FileUtils.readFileToByteArray(file);
            }
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Failed to perform drive benchmark operations.");
            return 0.0;
        }

        long endTime = System.nanoTime();
        long duration = (endTime - startTime);

        sender.sendMessage(ChatColor.YELLOW + "Benchmark complete.");

        // Calculate the score based on the duration
        return 1000000000.0 / (duration / 1000000.0);
    }

    private enum DriveResult {
        ULTRA_SLOW("Ultra Slow!"), VERY_SLOW("Very Slow!"), SLOW("Slow!"), AVERAGE("Average."),
        GOOD("Good!"), FAST("Fast!"), VERY_FAST("Very fast!"), ULTRA_FAST("Ultra Fast"), INSANELY_FAST("Insanely Fast!");

        private final String label;

        DriveResult(String label) {
            this.label = label;
        }

        public static String getSpeedLabel(double speed) {
            TreeMap<Double, DriveResult> speedMap = new TreeMap<>();
            speedMap.put(200000.0, INSANELY_FAST);
            speedMap.put(150000.0, ULTRA_FAST);
            speedMap.put(100000.0, VERY_FAST);
            speedMap.put(50000.0, FAST);
            speedMap.put(25000.0, GOOD);
            speedMap.put(10000.0, AVERAGE);
            speedMap.put(5000.0, SLOW);
            speedMap.put(1000.0, VERY_SLOW);

            for (double speedThreshold : speedMap.descendingKeySet()) {
                if (speed > speedThreshold) {
                    return speedMap.get(speedThreshold).label;
                }
            }

            return ULTRA_SLOW.label;
        }
    }
}
