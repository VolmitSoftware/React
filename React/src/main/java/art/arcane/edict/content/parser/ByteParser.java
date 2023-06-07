package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

/**
 * The ByteParser class is an implementation of the EdictParser interface for parsing Byte values from a String.
 * It attempts to parse the provided string as a byte, returning a high-confidence Byte value if successful.
 * If the string ends with "b", this character is ignored during parsing.
 * If parsing fails, the parser returns a low-confidence default Byte value of 0.
 */
public class ByteParser implements EdictParser<Byte> {
    @Override
    public EdictValue<Byte> parse(String s) {
        try {
            if (s.toLowerCase().endsWith("b")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Byte.parseByte(s));
        } catch (NumberFormatException e) {
            return low((byte) 0);
        }
    }
}
