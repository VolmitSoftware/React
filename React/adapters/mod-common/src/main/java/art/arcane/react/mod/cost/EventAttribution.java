package art.arcane.react.mod.cost;

/** Swaps timed wrappers into one loader's event dispatch and back. All calls run on the server thread. */
public interface EventAttribution {
    void install() throws ReflectiveOperationException;

    /** Re-wraps listener sets the loader rebuilt since the last pass. */
    void reconcile() throws ReflectiveOperationException;

    void uninstall() throws ReflectiveOperationException;
}
