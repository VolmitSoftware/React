package com.volmit.react.api.action;

import art.arcane.chrono.PrecisionStopwatch;
import com.volmit.react.React;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Data
public class ActionTicket<T extends ActionParams> {
    private Action<T> action;
    private T params;
    private boolean done;
    private boolean running;
    private long completedAt;
    private double duration;
    private long startedAt;
    private List<Consumer<ActionTicket<T>>> onComplete;
    private List<Consumer<ActionTicket<T>>> onStart;
    private int work;
    private int totalWork;
    private int count;
    private PrecisionStopwatch psw;

    public ActionTicket(Action<T> action, T params) {
        this.action = action;
        this.params = params;
        this.onComplete = new ArrayList<>();
        this.onStart = new ArrayList<>();
        this.startedAt = 0;
        this.completedAt = 0;
        work = 0;
        duration = -1;
        totalWork = 1;
        count = 0;
    }

    public void addCount(int c)
    {
        	count += c;
    }

    public void addCount()
    {
        	count++;
    }

    public void addWork(int w) {
        totalWork += w;
    }

    public void addWork() {
        totalWork++;
    }

    public double getProgress() {
        return getWork() / (double) getTotalWork();
    }

    public ActionTicket<T> onComplete(Consumer<ActionTicket<T>> r) {
        onComplete.add(r);
        return this;
    }

    public ActionTicket<T> onStart(Consumer<ActionTicket<T>> r) {
        onStart.add(r);
        return this;
    }

    public void queue() {
        React.instance.getActionController().queueAction(this);
    }

    public void start() {
        psw = PrecisionStopwatch.start();
        startedAt = System.currentTimeMillis();
        running = true;
        onStart.forEach(i -> i.accept(this));
    }

    public void complete() {
        duration = psw.getMilliseconds();
        completedAt = System.currentTimeMillis();
        if (done) {
            return;
        }

        done = true;
        onComplete.forEach(i -> i.accept(this));
    }
}
