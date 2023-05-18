package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

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
