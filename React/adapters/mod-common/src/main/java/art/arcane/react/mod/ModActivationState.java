package art.arcane.react.mod;

/**
 * The server tick until which React's activation range keeps an entity active, added to every entity by
 * React's entity activation mixin.
 */
public interface ModActivationState {
    long react$activatedTick();

    void react$activatedTick(long tick);
}
