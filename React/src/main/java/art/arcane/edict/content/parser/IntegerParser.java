package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

/**
 * The IntegerParser class is an implementation of the EdictParser interface, specifically
 * designed to parse strings into Integer objects.
 * <p>
 * The class overrides the parse method of the EdictParser interface. It first checks if the
 * string ends with the character 'i' (in a case-insensitive manner), and if it does, it removes this
 * character. Then it attempts to parse the remaining string into an integer.
 * <p>
 * If the parsing is successful, it returns an EdictValue containing the parsed integer and high confidence.
 * In case a NumberFormatException is thrown during the parsing process, it returns an EdictValue with
 * a default value of 0 and low confidence, indicating a less certain or incorrect parsing.
 *
 * @see EdictParser
 */
public class IntegerParser implements EdictParser<Integer> {
    @Override
    public EdictValue<Integer> parse(String s) {
        try {
            if(s.toLowerCase().endsWith("i")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Integer.parseInt(s));
        }

        catch(NumberFormatException e) {
            return low(0);
        }
    }
}
