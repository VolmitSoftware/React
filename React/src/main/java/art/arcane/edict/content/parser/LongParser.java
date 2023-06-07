package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

/**
 * The LongParser class is an implementation of the EdictParser interface that handles the parsing of
 * string representations into Long objects.
 * <br><br>
 * It overrides the parse method from the EdictParser interface. If the string to be parsed ends with 'l'
 * (in a case-insensitive manner), this character is removed before the parsing attempt.
 * <br><br>
 * If the parsing is successful, it returns an EdictValue containing the parsed long and high confidence.
 * In the event of a NumberFormatException, it returns an EdictValue with a default value of 0L and low
 * confidence, indicating a less certain or incorrect parsing.
 *
 * @see EdictParser
 */
public class LongParser implements EdictParser<Long> {
    @Override
    public EdictValue<Long> parse(String s) {
        try {
            if (s.toLowerCase().endsWith("l")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Long.parseLong(s));
        } catch (NumberFormatException e) {
            return low(0L);
        }
    }
}
