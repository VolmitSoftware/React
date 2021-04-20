package com.volmit.react.util;

import primal.json.JSONObject;

public interface IController {
    void start();

    void stop();

    void tick();

    int getInterval();

    boolean isUrgent();

    double getTime();

    void setTime(double ms);

    void dump(JSONObject object);
}
