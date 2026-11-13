package art.arcane.react.api.web.dto;

import java.util.List;

/** The calling token: its role, scopes and when the pairing ends unless used. */
public class WhoamiDto {
    public String tokenId;
    public String label;
    public String role;
    public List<String> scopes;
    public String expiresAt;
    public long lifetimeSeconds;
}
