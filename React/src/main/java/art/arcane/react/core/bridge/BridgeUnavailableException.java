package art.arcane.react.core.bridge;

public final class BridgeUnavailableException extends RuntimeException {
    public BridgeUnavailableException(String logicalId, String reason) {
        super("Bridge '" + logicalId + "' unavailable: " + reason);
    }
}
