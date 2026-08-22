package org.example.planning;

import org.example.environment.Vector3d;

/**
 * Kodowanie ruchu: wszystkie wektory całkowite (dx,dy,dz) ∈ {-1,0,1}³ poza (0,0,0) —
 * 26 kierunków siatki 3D (osie, skosy płaszczyzn i skosy przestrzenne).
 */
public final class MoveEncoding {

    private static final Vector3d[] DELTAS = buildDeltas();

    public static final int COUNT = DELTAS.length;

    private MoveEncoding() {
    }

    private static Vector3d[] buildDeltas() {
        Vector3d[] deltas = new Vector3d[26];
        int index = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        deltas[index++] = new Vector3d(dx, dy, dz);
                    }
                }
            }
        }
        return deltas;
    }

    public static Vector3d delta(int code) {
        if (code < 0 || code >= COUNT) {
            throw new IllegalArgumentException("Nieprawidłowy kod ruchu: " + code);
        }
        return DELTAS[code];
    }

    /** Kod ruchu dla wektora (dx,dy,dz) ∈ {-1,0,1}³ \ {0}. */
    public static int codeOf(int dx, int dy, int dz) {
        for (int code = 0; code < COUNT; code++) {
            Vector3d d = DELTAS[code];
            if (d.getX() == dx && d.getY() == dy && d.getZ() == dz) {
                return code;
            }
        }
        throw new IllegalArgumentException("Brak kodu ruchu dla (" + dx + "," + dy + "," + dz + ")");
    }
}
