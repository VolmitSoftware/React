package art.arcane.edict.api;

/**
 * Used to determine the confidence of a parsed value.
 * Higher confidence means that the value returned is guaranteed to be what the string represents.
 * Lower confidence means that the value returned is not guaranteed to be what the string represents and was likely guessed based
 * off of the input. This is useful because if we expect multi-type plugging, we will choose higher confidence results over lower
 * confidence results when both types would be accepted.
 */
public enum Confidence {
    /**
     * For example if its an integer and the input is "*". This cant be converted to hex at all. In this case the native
     * default is used (0) with an invalid confidence.
     */
    INVALID,

    /**
     * For example if it's an enum of {APPLE, ORANGE, PINEAPPLE} and they input "ange" then we can be pretty sure they meant
     * ORANGE however this would be a low confidence result. Another example would be if they type 'F' for an integer parser...
     * It could be assumed as 16 in hex? But its probably wrong.
     */
    LOW,

    /**
     * Only use high confidence if the calling toString on the object would result in the input string or very close to it (case)
     */
    HIGH,
}
