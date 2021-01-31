package com.volmit.react.api;

import primal.util.text.C;

public interface ISampler {
    String getID();

    void setID(String id);

    IFormatter getFormatter();

    String getName();

    void setName(String name);

    String getDescription();

    void setDescription(String description);

    C getColor();

    C getAltColor();

    void setColor(C color, C alt);

    int getInterval();

    void setInterval(int interval);

    void sample();

    String get();

    void construct();

    double getValue();

    void setValue(double v);
}
