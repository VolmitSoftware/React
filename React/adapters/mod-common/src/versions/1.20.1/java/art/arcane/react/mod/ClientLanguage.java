package art.arcane.react.mod;

/** The client language a 1.20.1 player last reported, kept on the player by a mixin. */
public interface ClientLanguage {
    String DEFAULT = "en_us";

    String react$language();
}
