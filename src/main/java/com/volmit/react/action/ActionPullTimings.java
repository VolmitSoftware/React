package com.volmit.react.action;

import com.volmit.react.Gate;
import com.volmit.react.api.*;
import com.volmit.react.util.AccessCallback;
import com.volmit.react.util.Callback;
import com.volmit.react.util.F;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import primal.util.text.C;

public class ActionPullTimings extends Action {
    public ActionPullTimings() {
        super(ActionType.TIMINGS);
        setNodes("timings", "time", "times", "tr");

        setDefaultSelector(Long.class, new AccessCallback<ISelector>() {
            @Override
            public ISelector get() {
                SelectorTime sel = new SelectorTime();
                sel.set((long) 3000);

                return sel;
            }
        });
    }

    @Override
    public void enact(IActionSource source, ISelector... selectors) {
        long timeFor = 3000;
        for (ISelector i : selectors) {
            if (i.getType().equals(Long.class)) {
                timeFor = ((SelectorTime) i).get();
            }
        }

        source.sendResponseActing("Pulling Timings for " + F.time(timeFor, 1));

        Gate.pullTimingsReport(timeFor, new Callback<String>() {
            @Override
            public void run(String t) {
                Gate.msgSuccess(((PlayerActionSource) source).getPlayer(), "Timings Captured " + C.WHITE + t);
                completeAction();
            }
        });
    }

    @Override
    public String getNode() {
        return "timings-report";
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Material.WRITTEN_BOOK);
    }
}
