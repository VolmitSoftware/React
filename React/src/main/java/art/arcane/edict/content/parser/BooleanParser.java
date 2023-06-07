package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

/**
 * The BooleanParser class is an implementation of the EdictParser interface for parsing Boolean values from a String.
 * It checks whether the provided string is "true" or "false" (ignoring case) and returns a corresponding Boolean value.
 * If the provided string is not "true" or "false", the parser returns a low-confidence Boolean value.
 */
public class BooleanParser implements EdictParser<Boolean> {
    @Override
    public EdictValue<Boolean> parse(String s) {
        boolean v = s.equalsIgnoreCase("true");

        if (v || s.equalsIgnoreCase("false")) {
            return high(v);
        }

        return low(v);
    }
}
