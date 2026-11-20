package art.arcane.react.api.web.dto;

/**
 * One loaded plugin or mod. {@code samplerId} names the cost sampler its time is reported under, which the server may
 * not have (React itself, or an owner without measured time); {@code includedIn} is set when the owner's time counts
 * under another owner's sampler.
 */
public class OwnerDto {
    public String id;
    public String name;
    public String version;
    public boolean enabled;
    public String samplerId;
    public String includedIn;
}
