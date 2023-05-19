package art.arcane.edict.content.parser;

import art.arcane.edict.api.Confidence;
import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

/**
 * The IntegerBinaryParser is a parser that parses binary string representations into integers.
 * It implements the EdictParser interface and its generic type is Integer.
 *
 * @see EdictParser
 */
public class IntegerBinaryParser implements EdictParser<Integer> {
    @Override
    public EdictValue<Integer> parse(String s) {
        Confidence c = Confidence.LOW;
        if(s.toLowerCase().endsWith("i")) {
            s = s.substring(0, s.length() - 1);
        }

        if(s.toLowerCase().startsWith("0b")) {
            s = s.substring(2);
            c = Confidence.HIGH;
        }

        try {
            return of(Integer.parseInt(s, 2), c);
        }

        catch(NumberFormatException e) {
            return low(0);
        }
    }
}
