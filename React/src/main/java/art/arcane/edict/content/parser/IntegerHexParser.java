package art.arcane.edict.content.parser;

import art.arcane.edict.api.Confidence;
import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

/**
 * The IntegerHexParser class is an implementation of the EdictParser interface that specifically
 * handles the parsing of hexadecimal string representations into Integer objects.
 * <p>
 * If the provided string is a valid hexadecimal representation (optionally starting with "0x" or "#"),
 * the parser will return an EdictValue with the parsed integer and high confidence.
 * If the parsing fails due to a NumberFormatException, it returns an EdictValue with a default value of 0
 * and low confidence, indicating a less certain or incorrect parsing.
 *
 * @see EdictParser
 */
public class IntegerHexParser implements EdictParser<Integer> {
    @Override
    public EdictValue<Integer> parse(String s) {
        Confidence c = Confidence.LOW;
        if(s.toLowerCase().endsWith("i")) {
            s = s.substring(0, s.length() - 1);
        }

        if(s.toLowerCase().startsWith("0x")) {
            s = s.substring(2);
            c = Confidence.HIGH;
        }

        if(s.startsWith("#")) {
            c = Confidence.HIGH;
        }

        try {
            return of(Integer.parseInt(s, 16), c);
        }

        catch(NumberFormatException e) {
            return low(0);
        }
    }
}
