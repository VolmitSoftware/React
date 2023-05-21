package com.volmit.react.content.tweak.matter;

import art.arcane.spatial.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

public class HopperIdentifierMatter extends RawMatter<HopperIdentifier> {
    public HopperIdentifierMatter(int w, int h, int d) {
        super(w, h, d, HopperIdentifier.class);
    }

    public HopperIdentifierMatter() {
        this(1, 1, 1);
    }

    @Override
    public void writeNode(HopperIdentifier HopperIdentifier, DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeLong(HopperIdentifier.getIdentifier().getMostSignificantBits());
        dataOutputStream.writeLong(HopperIdentifier.getIdentifier().getLeastSignificantBits());
    }

    @Override
    public HopperIdentifier readNode(DataInputStream dataInputStream) throws IOException {
        return new HopperIdentifier(new UUID(dataInputStream.readLong(), dataInputStream.readLong()));
    }
}
