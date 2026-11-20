package art.arcane.react.api.web.network;

/**
 * How React on a proxy and React on its backends talk. A backend posts a {@link NetworkRegistration}, sealed with the
 * shared network key, to {@link #REGISTER_PATH} on the proxy; the proxy then reaches the backend's own web listener
 * with the backend token the registration carries, narrowed to the browser's scopes by {@link #SCOPES_HEADER}.
 */
public final class NetworkProtocol {
  public static final String REGISTER_PATH = "/api/v1/network/register";
  /**
   * Each backend's API and sockets sit under {@code SERVERS_PATH/<server>/}. The prefix stays under {@code /api/v1/} so the
   * relay, which carries only API paths, reaches backends too.
   */
  public static final String SERVERS_PATH = "/api/v1/network/servers";
  public static final String SCOPES_HEADER = "X-React-Network-Scopes";
  /** The issuer of the backend token made for the proxy; only those tokens honour {@link #SCOPES_HEADER}. */
  public static final String TOKEN_ISSUER = "network";
  public static final String TOKEN_LABEL = "Network proxy";
  public static final long REGISTER_INTERVAL_MS = 15_000L;
  /** A backend that has not registered for this long is listed without React. */
  public static final long MEMBER_TIMEOUT_MS = 50_000L;
  /** How far a registration's clock may be from the proxy's. */
  public static final long MAXIMUM_CLOCK_SKEW_MS = 120_000L;

  private NetworkProtocol() {
  }
}
