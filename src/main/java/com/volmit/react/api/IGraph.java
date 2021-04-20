package com.volmit.react.api;

public interface IGraph {
    String getName();

    PlotBoard getPlotBoard();

    long getTimeViewport();

    void render(BufferedFrame frame);

    double getMax();

    void setMax(double max);
}
