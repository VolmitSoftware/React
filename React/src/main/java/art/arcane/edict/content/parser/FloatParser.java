package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

/**
 * The FloatParser class is an implementation of the EdictParser interface for parsing Float values from a String.
 * It attempts to parse the provided string as a float, returning a high-confidence Float value if successful.
 * If the string ends with "d", this character is ignored during parsing.
 * In case of a parsing failure (if the string cannot be parsed into a Float), the parser returns a low-confidence default Float value of 0.0F.
 */
public class FloatParser implements EdictParser<Float> {
    @Override
    public EdictValue<Float> parse(String s) {
        try {
            if(s.toLowerCase().endsWith("d")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Float.parseFloat(s));
        }

        catch(NumberFormatException e) {
            return low(0F);
        }
    }
}
