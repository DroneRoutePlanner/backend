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
        // Niższe „dno” mapy — większość terytorium zostaje płaska / niska.
        double baseHeight = 10 + random.nextDouble() * 14;

        double mapRadius = Math.hypot(width - 1, height - 1) / 2.0;
        if (mapRadius < 1e-6) {
            mapRadius = 1.0;
        }

        int minPeakDist = Math.max(8, (int) Math.round(Math.min(width, height) / 4.0));
        int peak1X = random.nextInt(width);
        int peak1Y = random.nextInt(height);
        int peak2X;
        int peak2Y;
        int guard = 0;
        do {
            peak2X = random.nextInt(width);
            peak2Y = random.nextInt(height);
            guard++;
        } while (squaredDist(peak1X, peak1Y, peak2X, peak2Y) < minPeakDist * minPeakDist
                && guard < 80);
        if (guard >= 80) {
            peak2X = (peak1X + minPeakDist) % width;
            peak2Y = (peak1Y + minPeakDist / 2) % height;
        }

        // Szczyty lokalne — stosunkowo niewielki ułamek mapy wysoki (reszta przy bazie).
        double peakHeight1 = 72 + random.nextDouble() * 20;
        double peakHeight2 = 62 + random.nextDouble() * 22;

        // Wąskie Gaussy: góry zajmują znacznie mniejszą powierzchnię niż przy σ ~ 0.2–0.6 × mapRadius.
        double sigma1 = mapRadius * (0.055 + random.nextDouble() * 0.09);
        double sigma2 = mapRadius * (0.055 + random.nextDouble() * 0.09);
        double sigma1Sq = sigma1 * sigma1;
        double sigma2Sq = sigma2 * sigma2;

        int noiseAmp = 2 + random.nextInt(6);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double f1 = gaussianFalloff(x, y, peak1X, peak1Y, sigma1Sq);
                double f2 = gaussianFalloff(x, y, peak2X, peak2Y, sigma2Sq);

                double h = baseHeight
                        + (peakHeight1 - baseHeight) * f1
                        + (peakHeight2 - baseHeight) * f2;
                h += (random.nextDouble() * 2.0 - 1.0) * noiseAmp;

                int z = (int) Math.round(h);
                if (z < 0) {
                    z = 0;
                }
                if (z > 100) {
                    z = 100;
                }
                heightMap[x][y] = z;
            }
        }

        System.out.printf(
                "Terrain: góra1 (%d,%d) h≈%.0f σ=%.2f | góra2 (%d,%d) h≈%.0f σ=%.2f | dno≈%.0f szum±%d%n",
                peak1X, peak1Y, peakHeight1, sigma1,
                peak2X, peak2Y, peakHeight2, sigma2,
                baseHeight, noiseAmp
        );
    }

    private static int squaredDist(int ax, int ay, int bx, int by) {
        int dx = ax - bx;
        int dy = ay - by;
        return dx * dx + dy * dy;
    }

    private static double gaussianFalloff(int x, int y, int peakX, int peakY, double sigmaSq) {
        double dx = x - peakX;
        double dy = y - peakY;
        return Math.exp(-(dx * dx + dy * dy) / (2.0 * sigmaSq));
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
