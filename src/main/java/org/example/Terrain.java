package org.example;

import java.util.Random;

public class Terrain {

    private final int[][] heightMap;

    private final int width;

    private final int height;

    private final Random random = new Random();

    public Terrain(int width, int height) {
        this.width = width;
        this.height = height;
        heightMap = new int[width][height];

        generateTerrain();
    }

    private void generateTerrain() {
        final int maxDelta = 50; // maksymalna zmiana między sąsiadami

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                int left = x > 0 ? heightMap[x - 1][y] : -1;
                int top = y > 0 ? heightMap[x][y - 1] : -1;

                int sum = 0;
                int count = 0;

                if (left >= 0) {
                    sum += left;
                    count++;
                }
                if (top >= 0) {
                    sum += top;
                    count++;
                }

                int avg;
                if (count > 0) {
                    avg = sum / count;
                } else {
                    avg = 50; // pierwsza komórka (0,0) ustawiona na środek 0..100
                }

                int delta = random.nextInt(maxDelta * 2 + 1) - maxDelta; // -maxDelta..+maxDelta
                heightMap[x][y] = avg + delta;

                // ograniczenia 0..100
                if (heightMap[x][y] < 0) heightMap[x][y] = 0;
                if (heightMap[x][y] > 100) heightMap[x][y] = 100;
            }
        }

        // ---- PRINT HEIGHT MAP ----
        System.out.println("Generated Terrain HeightMap:");
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                System.out.print(heightMap[x][y] + "\t");
            }
            System.out.println();
        }
        System.out.println("---------------------------");
    }

    public int getHeight(int x, int y) {
        return heightMap[x][y];
    }

    public int getWidth() {
        return width;
    }

    public int getHeightMapHeight() {
        return height;
    }
}
