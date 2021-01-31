package com.volmit.react.util;

import java.io.*;

public class BufferedStreamConstructor implements StreamConstructor<BufferedInputStream, BufferedOutputStream> {
    private final int bufferSize;

    public BufferedStreamConstructor(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public BufferedStreamConstructor() {
        this(8192);
    }

    @Override
    public BufferedInputStream constructInput(InputStream base) throws IOException {
        return new BufferedInputStream(base, bufferSize);
    }

    @Override
    public BufferedOutputStream constructOutput(OutputStream base) throws IOException {
        return new BufferedOutputStream(base, bufferSize);
    }

}
