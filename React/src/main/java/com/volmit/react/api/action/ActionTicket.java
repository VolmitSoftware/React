package com.volmit.react.api.action;

import com.volmit.react.React;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionTicket<T extends ActionParams> {
    private Action<T> action;
    private T params;
    private boolean done;
    private boolean running;
    private long startedAt;
    private List<Runnable> onComplete;
    private List<Runnable> onStart;
    private int work;
    private int totalWork;

    public ActionTicket(Action<T> action, T params) {
        this.action = action;
        this.params = params;
        this.onComplete = new ArrayList<>();
        this.onStart = new ArrayList<>();
        this.startedAt = 0;
        work = 0;
        totalWork = 1;
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

    public ActionTicket<T> onComplete(Runnable r) {
        onComplete.add(r);
        return this;
    }

    public ActionTicket<T> onStart(Runnable r) {
        onStart.add(r);
        return this;
    }

    public void queue() {
        React.instance.getActionController().queueAction(this);
    }

    public void start() {
        startedAt = System.currentTimeMillis();
        running = true;
        onStart.forEach(Runnable::run);
    }

    public void complete() {
        if (done) {
            return;
        }

        done = true;
        onComplete.forEach(Runnable::run);
    }
}
