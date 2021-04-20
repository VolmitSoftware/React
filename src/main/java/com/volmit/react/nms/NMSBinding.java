package com.volmit.react.nms;

public abstract class NMSBinding implements INMSBinding {
    private final String packageVersion;

    public NMSBinding(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    @Override
    public String getPackageVersion() {
        return packageVersion;
    }
}
