package art.arcane.react.api.web.network;

/**
 * What a backend tells the proxy each time it registers. {@code serverName} is the backend's name in the proxy's server
 * list when set in its web.toml, else empty and the proxy matches {@code gamePort}. {@code bearer} is the backend token
 * the proxy uses to reach {@code webPort}.
 */
public record NetworkRegistration(String serverName, String displayName, int gamePort, int webPort, String bearer,
                                  String fingerprint, String platform, String loader, String version, long sentAtMs) {
}
