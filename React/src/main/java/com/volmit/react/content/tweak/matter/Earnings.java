package com.volmit.react.content.tweak.matter;


import art.arcane.spatial.matter.slices.RawMatter;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Data
@AllArgsConstructor
public class Earnings {
    private final int earnings;

    public Earnings increment() {
        if (earnings >= 127) {
            return this;
        }

        return new Earnings(getEarnings() + 1);
    }

    public static class EarningsMatter extends RawMatter<Earnings> {
        public EarningsMatter() {
            this(1, 1, 1);
        }

        public EarningsMatter(int width, int height, int depth) {
            super(width, height, depth, Earnings.class);
        }

        @Override
        public void writeNode(Earnings earnings, DataOutputStream dataOutputStream) throws IOException {
            dataOutputStream.writeByte(earnings.getEarnings());
        }

        @Override
        public Earnings readNode(DataInputStream dataInputStream) throws IOException {
            return new Earnings(dataInputStream.readByte());
        }
    }
}
