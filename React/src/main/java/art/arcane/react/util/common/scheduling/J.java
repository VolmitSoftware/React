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

package art.arcane.react.util.scheduling;

import art.arcane.curse.Curse;
import art.arcane.multiburst.MultiBurst;
import art.arcane.react.React;
import art.arcane.react.core.controller.JobController;
import art.arcane.volmlib.util.function.NastyFunction;
import art.arcane.volmlib.util.function.NastyFuture;
import art.arcane.volmlib.util.function.NastyRunnable;
import art.arcane.volmlib.util.scheduling.AR;
import art.arcane.volmlib.util.scheduling.JSupport;
import art.arcane.volmlib.util.scheduling.SR;
import art.arcane.volmlib.util.scheduling.SchedulerBridge;
import art.arcane.volmlib.util.scheduling.StartupQueueSupport;
import art.arcane.volmlib.util.math.FinalInteger;
import org.bukkit.Bukkit;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class J {
    private static final int tid = 0;
    private static final StartupQueueSupport STARTUP_QUEUE = new StartupQueueSupport();

    static {
        SchedulerBridge.setSyncScheduler(J::s);
        SchedulerBridge.setDelayedSyncScheduler(J::s);
        SchedulerBridge.setAsyncScheduler(J::a);
        SchedulerBridge.setDelayedAsyncScheduler(J::a);
        SchedulerBridge.setSyncRepeatingScheduler(J::sr);
        SchedulerBridge.setAsyncRepeatingScheduler(J::ar);
        SchedulerBridge.setCancelScheduler(J::car);
        SchedulerBridge.setErrorHandler(e -> {
            React.reportError(e);
            e.printStackTrace();
        });
        SchedulerBridge.setInfoLogger(React::info);
    }

    public static void dofor(int a, Function<Integer, Boolean> c, int ch, Consumer<Integer> d) {
        JSupport.dofor(a, c, ch, d);
    }

    public static boolean doif(Supplier<Boolean> c, Runnable g) {
        return JSupport.doif(c, g, null);
    }

    public static void a(Runnable a) {
        MultiBurst.burst.lazy(a);
    }

    public static <T> Future<T> a(Callable<T> a) {
        return ((ExecutorService) Curse.on(MultiBurst.burst).get("service")).submit(a);
    }

    public static void attemptAsync(NastyRunnable r) {
        JSupport.attemptAsync(r::run, J::a);
    }

    public static <R> R attemptResult(NastyFuture<R> r, R onError) {
        return JSupport.attemptResult(r::run, onError, Throwable::printStackTrace);
    }

    public static <T, R> R attemptFunction(NastyFunction<T, R> r, T param, R onError) {
        return JSupport.attemptFunction(r::run, param, onError, null);
    }

    public static boolean sleep(long ms) {
        return JSupport.sleep(ms);
    }

    public static boolean attempt(NastyRunnable r) {
        return JSupport.attempt(r::run);
    }

    public static Throwable attemptCatch(NastyRunnable r) {
        return JSupport.attemptCatch(r::run);
    }

    public static <T> T attempt(Supplier<T> t, T i) {
        return JSupport.attempt(t::get, i, null);
    }

    /**
     * Dont call this unless you know what you are doing!
     */
    public static void executeAfterStartupQueue() {
        JSupport.executeAfterStartupQueue(STARTUP_QUEUE, J::s, J::a);
    }

    /**
     * Schedule a sync task to be run right after startup. If the server has already
     * started ticking, it will simply run it in a sync task.
     * <br><br>
     * If you dont know if you should queue this or not, do so, it's pretty
     * forgiving.
     *
     * @param r the runnable
     */
    public static void ass(Runnable r) {
        JSupport.enqueueAfterStartupSync(STARTUP_QUEUE, r, J::s);
    }

    /**
     * Schedule an async task to be run right after startup. If the server has
     * already started ticking, it will simply run it in an async task.
     * <br><br>
     * If you dont know if you should queue this or not, do so, it's pretty
     * forgiving.
     *
     * @param r the runnable
     */
    public static void asa(Runnable r) {
        JSupport.enqueueAfterStartupAsync(STARTUP_QUEUE, r, J::a);
    }

    public static <T> T sResult(Supplier<T> t) {
        if (Bukkit.isPrimaryThread()) {
            return t.get();
        }

        AtomicBoolean f = new AtomicBoolean(false);
        AtomicReference<T> r = new AtomicReference<>();
        J.s(() -> {
            r.set(t.get());
            f.set(true);
        });

        while (!f.get()) {
            J.sleep(15);
        }

        return r.get();
    }

    /**
     * Queue a sync task
     *
     * @param r the runnable
     */
    public static void s(Runnable r) {
        if (!React.instance.isReady()) {
            React.instance.getPrejobs().add(r);
            return;
        }

        React.controller(JobController.class).queue(r);
    }

    /**
     * Queue a sync task
     *
     * @param r     the runnable
     * @param delay the delay to wait in ticks before running
     */
    public static void s(Runnable r, int delay) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(React.instance, () -> React.controller(JobController.class).queue(r), delay);
    }

    public static void ss(Runnable r, int delay) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(React.instance, r, delay);
    }

    public static void ss(Runnable r) {
        J.ss(r, 0);
    }

    /**
     * Cancel a sync repeating task
     *
     * @param id the task id
     */
    public static void csr(int id) {
        Bukkit.getScheduler().cancelTask(id);
    }

    /**
     * Start a sync repeating task
     *
     * @param r        the runnable
     * @param interval the interval
     * @return the task id
     */
    public static int sr(Runnable r, int interval) {
        return Bukkit.getScheduler().scheduleSyncRepeatingTask(React.instance, r, 0, interval);
    }

    /**
     * Start a sync repeating task for a limited amount of ticks
     *
     * @param r         the runnable
     * @param interval  the interval in ticks
     * @param intervals the maximum amount of intervals to run
     */
    public static void sr(Runnable r, int interval, int intervals) {
        FinalInteger fi = new FinalInteger(0);

        new SR(interval) {
            @Override
            public void run() {
                fi.add(1);
                r.run();

                if (fi.get() >= intervals) {
                    cancel();
                }
            }
        };
    }

    /**
     * Call an async task dealyed
     *
     * @param r     the runnable
     * @param delay the delay to wait before running
     */
    @SuppressWarnings("deprecation")
    public static void a(Runnable r, int delay) {
        Bukkit.getScheduler().scheduleAsyncDelayedTask(React.instance, r, delay);
    }

    /**
     * Cancel an async repeat task
     *
     * @param id the id
     */
    public static void car(int id) {
        Bukkit.getScheduler().cancelTask(id);
    }

    /**
     * Start an async repeat task
     *
     * @param r        the runnable
     * @param interval the interval in ticks
     * @return the task id
     */
    @SuppressWarnings("deprecation")
    public static int ar(Runnable r, int interval) {
        return Bukkit.getScheduler().scheduleAsyncRepeatingTask(React.instance, r, 0, interval);
    }

    /**
     * Start an async repeating task for a limited time
     *
     * @param r         the runnable
     * @param interval  the interval
     * @param intervals the intervals to run
     */
    public static void ar(Runnable r, int interval, int intervals) {
        FinalInteger fi = new FinalInteger(0);

        new AR(interval) {
            @Override
            public void run() {
                fi.add(1);
                r.run();

                if (fi.get() >= intervals) {
                    cancel();
                }
            }
        };
    }
}
