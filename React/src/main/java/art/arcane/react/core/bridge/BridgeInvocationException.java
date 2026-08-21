package art.arcane.react.core.bridge;

public final class BridgeInvocationException extends RuntimeException {
    public BridgeInvocationException(String logicalId, Throwable cause) {
        super("Bridge '" + logicalId + "' invocation failed: " + cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : " - " + cause.getMessage()), cause);
    }
}
