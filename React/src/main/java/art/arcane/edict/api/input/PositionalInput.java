package art.arcane.edict.api.input;

/**
 * The PositionalInput interface represents a specialized form of an EdictInput in the Edict command handling system
 * where the input is identified by its position in the command arguments.
 * <br><br>
 * The interface provides methods to retrieve the real position and the positional position of the input. The real
 * position is the actual location of the input in the command string, while the positional position is calculated by
 * only counting positional inputs, ignoring any mapped parameters.
 */
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
