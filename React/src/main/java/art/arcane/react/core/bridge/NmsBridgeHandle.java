package art.arcane.react.core.bridge;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

public final class NmsBridgeHandle {
    private final BridgeResolution resolution;
    private final MethodHandle methodHandle;
    private final VarHandle varHandle;

    NmsBridgeHandle(BridgeResolution resolution, MethodHandle methodHandle) {
        this.resolution = resolution;
        this.methodHandle = methodHandle;
        this.varHandle = null;
    }

    NmsBridgeHandle(BridgeResolution resolution, VarHandle varHandle) {
        this.resolution = resolution;
        this.methodHandle = null;
        this.varHandle = varHandle;
    }

    NmsBridgeHandle(BridgeResolution resolution) {
        this.resolution = resolution;
        this.methodHandle = null;
        this.varHandle = null;
    }

    public boolean available() {
        return resolution.available();
    }

    public BridgeResolution resolution() {
        return resolution;
    }

    public MethodHandle methodHandle() {
        if (methodHandle == null) {
            throw new BridgeUnavailableException(resolution.logicalId(), resolution.failureReason());
        }
        return methodHandle;
    }

    public VarHandle varHandle() {
        if (varHandle == null) {
            throw new BridgeUnavailableException(resolution.logicalId(), resolution.failureReason());
        }
        return varHandle;
    }
}
