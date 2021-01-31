package com.volmit.react.api;

import primal.lang.collection.GList;
import primal.util.text.C;

public class MonitorHeading {
    private ISampler head;
    private GList<ISampler> children;
    private String name;

    public MonitorHeading(String name, ISampler head) {
        this.name = name;
        this.head = head;
        children = new GList<ISampler>();
    }

    public void addSampler(ISampler s) {
        children.add(s);
    }

    public ISampler getHead() {
        return head;
    }

    public void setHead(ISampler head) {
        this.head = head;
    }

    public GList<ISampler> getChildren() {
        return children;
    }

    public void setChildren(GList<ISampler> children) {
        this.children = children;
    }

    public String getHeadText() {
        return getHead().getColor() + getHead().get() + C.RESET + getHead().getColor() + " " + getHead().getID();
    }

    public String getChildText() {
        StringBuilder m = new StringBuilder();

        for (ISampler i : getChildren()) {
            String p = i.getColor() + i.get() + C.RESET + i.getColor() + " " + i.getID();
            m.append(" ").append(p);
        }

        if (m.length() < 2) {
            return m.toString();
        }

        return m.substring(1);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
