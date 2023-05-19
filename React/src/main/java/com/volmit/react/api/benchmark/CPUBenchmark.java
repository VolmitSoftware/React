package com.volmit.react.api.benchmark;

import com.volmit.react.util.plugin.VolmitSender;
import com.volmit.react.util.scheduling.J;
import org.bukkit.ChatColor;

import java.util.TreeMap;

public class CPUBenchmark implements Runnable {
    private VolmitSender sender;

    public CPUBenchmark(VolmitSender sender) {
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
        return (((int) (1000000000.0 / (duration / 1000000.0)))/10000);
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
