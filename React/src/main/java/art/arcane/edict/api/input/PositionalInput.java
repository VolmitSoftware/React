package art.arcane.edict.api.input;

public interface PositionalInput {
    /**
     * The real position of this input mapped from the string
     * @return the real position
     */
    int getRealPosition();

    /**
     * The position starting at 0, indexed by the POSITIONAL type only.
     * This means if there is a mapped parameter before this input it is ignored and wont increment.
     * This ensures that if there is 3 positional inputs there will only be 0 1 and 2 even if there are mapped params
     * in between the positional inputs.
     * @return the positional position
     */
    int getPosition();
}
