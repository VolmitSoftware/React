package art.arcane.react.api.web.dto;

import java.util.List;

/** {@code POST /api/v1/pair}: the bearer, shown once, and what the pairing allows. */
public class PairResponseDto {
    public String token;
    public String tokenId;
    public String label;
    public String role;
    public List<String> scopes;
    public String expiresAt;
    public long lifetimeSeconds;
    public String fingerprint;
    public String serverName;
}
