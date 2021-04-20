package com.volmit.react.api;

import com.volmit.react.xrai.GoalManager;
import primal.lang.collection.GList;

public interface IRAI {
    GoalManager getGoalManager();

    void tick();

    GList<RAIEvent> getEvents();

    void callEvent(RAIEvent e);

    GList<IActionSource> getListeners();
}
