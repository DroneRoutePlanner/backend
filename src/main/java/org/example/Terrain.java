package org.example;

import java.util.Random;

/**
 * Mapa wysokości terenu (wartości surowe 0–{@value #MAX_RAW_HEIGHT}) indeksowana {@code [x][y]}.
 * Generator jest deterministyczny dla zadanego ziarna, co pozwala powtarzać eksperymenty.
 */
public final class Terrain {

    public static final int MAX_RAW_HEIGHT = 100;

    private static final int PEAK_COUNT = 4;

    private static final int PEAK_PLACEMENT_ATTEMPTS = 80;

    private final int[][] heightMap;

    private final int width;

    private final int depth;

    /** Losowy teren z losowym ziarnem (nieodtwarzalny — do szybkiego podglądu). */
    public Terrain(int width, int depth) {
        this(width, depth, new Random().nextLong());
    }

    /** Losowy teren odtwarzalny dla zadanego ziarna. */
    public Terrain(int width, int depth, long seed) {
        this(generate(width, depth, new Random(seed)));
    }

    private Terrain(int[][] heightMap) {
        this.width = heightMap.length;
        this.depth = heightMap[0].length;
        this.heightMap = heightMap;
    }

    /** Teren z jawnej mapy wysokości {@code heightMap[x][y]} (kopiowanej). */
    public static Terrain fromHeightMap(int[][] heightMap) {
        if (heightMap.length == 0 || heightMap[0].length == 0) {
            throw new IllegalArgumentException("Mapa wysokości musi mieć co najmniej 1×1 komórek");
        }
        int[][] copy = new int[heightMap.length][];
        for (int x = 0; x < heightMap.length; x++) {
            if (heightMap[x].length != heightMap[0].length) {
                throw new IllegalArgumentException("Mapa wysokości musi być prostokątna");
            }
            copy[x] = heightMap[x].clone();
        }
        return new Terrain(copy);
    }

    /** Płaski teren o stałej wysokości — wygodny w testach. */
    public static Terrain flat(int width, int depth, int rawHeight) {
        int[][] map = new int[width][depth];
        for (int[] column : map) {
            java.util.Arrays.fill(column, rawHeight);
        }
        return new Terrain(map);
    }

    private static int[][] generate(int width, int depth, Random random) {
        if (width < 1 || depth < 1) {
            throw new IllegalArgumentException("Wymiary terenu muszą być dodatnie");
        }
        int[][] map = new int[width][depth];

        // Niskie „dno” mapy — większość terytorium zostaje płaska / niska.
        double baseHeight = 4 + random.nextDouble() * 10;

        double mapRadius = Math.max(1.0, Math.hypot(width - 1, depth - 1) / 2.0);
        int minPeakDist = Math.max(6, (int) Math.round(Math.min(width, depth) / 5.0));
        int minPeakDistSq = minPeakDist * minPeakDist;

        int[] peakX = new int[PEAK_COUNT];
        int[] peakY = new int[PEAK_COUNT];
        double[] peakHeight = new double[PEAK_COUNT];
        double[] sigmaSq = new double[PEAK_COUNT];

        for (int p = 0; p < PEAK_COUNT; p++) {
            if (p == 0) {
                peakX[0] = random.nextInt(width);
                peakY[0] = random.nextInt(depth);
            } else {
                int attempts = 0;
                do {
                    peakX[p] = random.nextInt(width);
                    peakY[p] = random.nextInt(depth);
                    attempts++;
                } while (!isFarFromOtherPeaks(peakX, peakY, p, minPeakDistSq) && attempts < PEAK_PLACEMENT_ATTEMPTS);
                if (attempts >= PEAK_PLACEMENT_ATTEMPTS) {
                    peakX[p] = (peakX[p - 1] + minPeakDist * p) % width;
                    peakY[p] = (peakY[p - 1] + minPeakDist / 2) % depth;
                }
            }
            peakHeight[p] = 58 + random.nextDouble() * 20;
            double sigma = mapRadius * (0.10 + random.nextDouble() * 0.12);
            sigmaSq[p] = sigma * sigma;
        }

        int noiseAmplitude = 2 + random.nextInt(6);

        for (int y = 0; y < depth; y++) {
            for (int x = 0; x < width; x++) {
                double h = baseHeight;
                for (int p = 0; p < PEAK_COUNT; p++) {
                    h += (peakHeight[p] - baseHeight) * gaussianFalloff(x, y, peakX[p], peakY[p], sigmaSq[p]);
                }
                h += (random.nextDouble() * 2.0 - 1.0) * noiseAmplitude;
                map[x][y] = (int) Math.max(0, Math.min(MAX_RAW_HEIGHT, Math.round(h)));
            }
        }
        return map;
    }

    private static boolean isFarFromOtherPeaks(int[] peakX, int[] peakY, int index, int minDistSq) {
        for (int i = 0; i < index; i++) {
            int dx = peakX[index] - peakX[i];
            int dy = peakY[index] - peakY[i];
            if (dx * dx + dy * dy < minDistSq) {
                return false;
            }
        }
        return true;
    }

    private static double gaussianFalloff(int x, int y, int peakX, int peakY, double sigmaSq) {
        double dx = x - peakX;
        double dy = y - peakY;
        return Math.exp(-(dx * dx + dy * dy) / (2.0 * sigmaSq));
    }

    /** Surowa wysokość terenu (0–{@value #MAX_RAW_HEIGHT}) w komórce {@code (x, y)}. */
    public int getHeight(int x, int y) {
        return heightMap[x][y];
    }

    /** Liczba komórek w osi X. */
    public int getWidth() {
        return width;
    }

    /** Liczba komórek w osi Y. */
    public int getDepth() {
        return depth;
    }
}
