package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

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
