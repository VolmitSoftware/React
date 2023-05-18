package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

public class ByteParser implements EdictParser<Byte> {
    @Override
    public EdictValue<Byte> parse(String s) {
        try {
            if(s.toLowerCase().endsWith("b")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Byte.parseByte(s));
        }

        catch(NumberFormatException e) {
            return low((byte) 0);
        }
    }
}
