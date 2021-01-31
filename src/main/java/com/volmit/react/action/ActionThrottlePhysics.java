package com.volmit.react.action;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.api.*;
import com.volmit.react.util.*;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ActionThrottlePhysics extends Action {
    private long ms;
    private int lcd;
    private boolean fail;

    public ActionThrottlePhysics() {
        super(ActionType.THROTTLE_PHYSICS);
        fail = false;
        setNodes(Info.ACTION_THROTTLE_PHYSICS_TAGS);

        setDefaultSelector(Chunk.class, new AccessCallback<ISelector>() {
            @Override
            public ISelector get() {
                SelectorPosition sel = new SelectorPosition();
                sel.addAll();

                return sel;
            }
        });

        setDefaultSelector(Long.class, new AccessCallback<ISelector>() {
            @Override
            public ISelector get() {
                SelectorTime sel = new SelectorTime();
                sel.set((long) 5000);

                return sel;
            }
        });
    }

    @Override
    public void enact(IActionSource source, ISelector... selectors) {
        FinalInteger total = new FinalInteger(0);
        FinalInteger totalCulled = new FinalInteger(0);
        FinalInteger totalChunked = new FinalInteger(0);
        FinalInteger completed = new FinalInteger(0);
        FinalInteger acompleted = new FinalInteger(0);
        ms = M.ms();
        int tchu = 0;
        long timeFor = 3000;

        for (ISelector i : selectors) {
            if (i.getType().equals(Chunk.class)) {
                tchu += i.getPossibilities().size();
            }

            if (i.getType().equals(Long.class)) {
                timeFor = ((SelectorTime) i).get();
            }
        }

        source.sendResponseActing("Throttling " + F.f(tchu) + " chunk" + ((tchu > 1 || tchu == 0) ? "s" : "") + " for " + F.time(timeFor, 1)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        for (ISelector i : selectors) {
            if (i.getType().equals(Chunk.class)) {
                total.add(i.getPossibilities().size());
                double mk = 2;
                for (Object j : i.getPossibilities()) {
                    if (i.can(j)) {
                        long t = timeFor;
                        mk += 0.03;
                        int dk = (int) mk;
                        new Task("waiter-trx", 0, dk) //$NON-NLS-1$
                        {
                            @Override
                            public void run() {
                                if (dk - 1 == ticks) {
                                    throttle(t, (Chunk) j, new Runnable() {
                                        @Override
                                        public void run() {
                                            if (!fail) {
                                                acompleted.add(1);
                                            }

                                            completed.add(1);
                                            String s = Info.ACTION_THROTTLE_PHYSICS_STATUS;
                                            setProgress((double) completed.get() / (double) total.get());
                                            s = s.replace("$c", F.f(completed.get())); //$NON-NLS-1$
                                            s = s.replace("$t", F.f(total.get())); //$NON-NLS-1$
                                            s = s.replace("$p", F.pc(getProgress(), 0)); //$NON-NLS-1$
                                            setStatus(s);
                                            ms = M.ms();
                                            totalCulled.add(lcd);

                                            if (lcd > 0) {
                                                totalChunked.add(1);
                                            }

                                            if (completed.get() == total.get()) {
                                                completeAction();
                                                source.sendResponseSuccess("Throttled " + F.f(acompleted.get()) + " chunk" + ((acompleted.get() > 1 || acompleted.get() == 0) ? "s" : "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                                            }
                                        }
                                    }, source, selectors);

                                    cancel();
                                }
                            }
                        };
                    }
                }
            }
        }

        new Task("p-monitor-callback", 30) //$NON-NLS-1$
        {
            @Override
            public void run() {
                if (M.ms() - ms > 1000 && getState().equals(ActionState.RUNNING)) {
                    cancel();
                    completeAction();
                    source.sendResponseSuccess("Throttled " + F.f(acompleted.get()) + " chunk" + ((acompleted.get() > 1 || acompleted.get() == 0) ? "s" : "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                }
            }

        };
    }

    public void throttle(long time, Chunk chunk, Runnable cb, IActionSource source, ISelector... selectors) {
        try {

            fail = !Gate.throttleChunk(chunk, 20, time);

            ms = M.ms();
            cb.run();
        } catch (Throwable e) {
            new S("action.throttlechunk") {
                @Override
                public void run() {

                    fail = !Gate.throttleChunk(chunk, 20, time);

                    ms = M.ms();
                    cb.run();
                }
            };
        }
    }

    @Override
    public String getNode() {
        return "throttle-chunks";
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Material.REDSTONE_TORCH_ON);
    }
}
