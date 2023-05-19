package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;
import art.arcane.edict.content.context.LocationContext;
import art.arcane.edict.content.context.WorldContext;

/**
 * The ShortParser class is an implementation of the EdictParser interface designed to handle the parsing of
 * string representations into Short objects.
 * <p>
 * It overrides the parse method from the EdictParser interface. If the string to be parsed ends with 's'
 * (in a case-insensitive manner), this character is removed before the parsing attempt.
 * <p>
 * If the parsing is successful, it returns an EdictValue containing the parsed short and high confidence.
 * In the event of a NumberFormatException, it returns an EdictValue with a default value of 0 (cast to short)
 * and low confidence, indicating a less certain or incorrect parsing.
 *
 * @see EdictParser
 */
public class ShortParser implements EdictParser<Short> {
    @Override
    public EdictValue<Short> parse(String s) {
        try {
            if(s.toLowerCase().endsWith("s")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Short.parseShort(s));
        }

        catch(NumberFormatException e) {
            return low((short) 0);
        }
    }
}
