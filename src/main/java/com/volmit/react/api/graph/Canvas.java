package com.volmit.react.api.graph;

public class Canvas {
    private final int width;
    private final int height;
    private final int[] pixels;

    public Canvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    public void setPixel(int x, int y, int color) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }
        pixels[x + y * width] = color;
    }

    public int getPixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return 0;
        }
        return pixels[x + y * width];
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int[] getPixels() {
        return pixels;
    }
}
