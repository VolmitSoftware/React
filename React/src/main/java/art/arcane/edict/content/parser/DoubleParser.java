package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

public class DoubleParser implements EdictParser<Double> {
    @Override
    public EdictValue<Double> parse(String s) {
        try {
            if(s.toLowerCase().endsWith("d")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Double.parseDouble(s));
        }

        catch(NumberFormatException e) {
            return low(0D);
        }
    }
}
