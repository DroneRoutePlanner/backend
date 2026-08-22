package org.example.planning.io;

import org.example.Terrain;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Zapisuje heightmapę terenu do CSV (dla wykresu 3D w Pythonie).
 * <p>
 * Format: pierwszy wiersz {@code width,height}, potem {@code height} wierszy po {@code width}
 * wartości {@code raw} (0–100) jak {@link Terrain#getHeight(int, int)} — indeks kolumny = x, wiersza = y.
 */
public final class TerrainCsvWriter {

    private TerrainCsvWriter() {
    }

    public static void write(Terrain terrain, Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        int w = terrain.getWidth();
        int h = terrain.getDepth();
        try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            bw.write(Integer.toString(w));
            bw.write(',');
            bw.write(Integer.toString(h));
            bw.newLine();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (x > 0) {
                        bw.write(',');
                    }
                    bw.write(Integer.toString(terrain.getHeight(x, y)));
                }
                bw.newLine();
            }
        }
    }

    /**
     * Ścieżka towarzysząca plikowi trajektorii, np. {@code out/trasa.csv} → {@code out/trasa_terrain.csv}.
     */
    public static Path siblingTerrainPath(Path trajectoryCsv) {
        Path parent = trajectoryCsv.getParent();
        String name = trajectoryCsv.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String out = stem + "_terrain.csv";
        return parent == null ? Path.of(out) : parent.resolve(out);
    }
}
