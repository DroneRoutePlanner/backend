package org.example.planning.io;

import org.example.environment.Vector3d;
import org.example.planning.SimulationTrace;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Zapisuje przebieg pozycji dronów do CSV w formacie długim (jeden wiersz = jeden dron w jednym kroku),
 * nadającym się do wykresu 3D w Pythonie (matplotlib / plotly).
 * <p>
 * Kolumny: {@code step,drone_id,x,y,z} — {@code step} od 0, {@code drone_id} od 0.
 * <p>
 * Przy zapisie trajektorii aplikacja zapisuje też heightmapę obok pliku: {@code *_terrain.csv}
 * (patrz {@link org.example.planning.io.TerrainCsvWriter}).
 * <p>
 * Przykład z Gradle: {@code ./gradlew -Ddrone.trajectory.csv=assets/trasa.csv run}
 * (właściwość musi być przekazana do JVM aplikacji — patrz {@code build.gradle}).
 */
public final class TrajectoryCsvWriter {

    private static final String HEADER = "step,drone_id,x,y,z";

    private TrajectoryCsvWriter() {
    }

    public static void write(SimulationTrace trace, Path path) throws IOException {
        write(trace.positionsPerStep(), path);
    }

    /**
     * @param positionsPerStep klatka {@code t}: lista pozycji dronów (indeks = id drona)
     */
    public static void write(List<List<Vector3d>> positionsPerStep, Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(HEADER);
            w.newLine();
            for (int t = 0; t < positionsPerStep.size(); t++) {
                List<Vector3d> snap = positionsPerStep.get(t);
                for (int d = 0; d < snap.size(); d++) {
                    Vector3d p = snap.get(d);
                    w.write(Integer.toString(t));
                    w.write(',');
                    w.write(Integer.toString(d));
                    w.write(',');
                    w.write(Integer.toString(p.getX()));
                    w.write(',');
                    w.write(Integer.toString(p.getY()));
                    w.write(',');
                    w.write(Integer.toString(p.getZ()));
                    w.newLine();
                }
            }
        }
    }
}
