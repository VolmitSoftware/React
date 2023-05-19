package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

/**
 * The StringParser class is an implementation of the EdictParser interface designed specifically to handle
 * the parsing of string representations into String objects.
 * <p>
 * It overrides the parse method from the EdictParser interface. As this class is designed to parse strings,
 * the parse method simply returns the input string within an EdictValue, with a high confidence level.
 * No parsing exceptions are expected in this case, as the input is already a string.
 *
 * @see EdictParser
 */
public class StringParser implements EdictParser<String> {
    @Override
    public EdictValue<String> parse(String s) {
        return high(s);
    }
}
