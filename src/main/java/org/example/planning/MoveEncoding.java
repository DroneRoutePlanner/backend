package org.example.planning;

import org.example.environment.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Kody ruchu dla planowania: wszystkie wektory całkowite (dx,dy,dz) z {-1,0,1}³
 * poza (0,0,0) — 26 kierunków: osie, skosy płaszczyzn i skosy przestrzenne (węzeł 3D).
 */
public final class MoveEncoding {

    private static final int[][] DELTAS;

    public static final int COUNT;

    static {
        List<int[]> list = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    list.add(new int[]{dx, dy, dz});
                }
            }
        }
        DELTAS = list.toArray(new int[0][]);
        COUNT = DELTAS.length;
    }

    private MoveEncoding() {
    }

    public static Vector3d delta(int code) {
        if (code < 0 || code >= COUNT) {
            return new Vector3d(0, 0, 0);
        }
        int[] d = DELTAS[code];
        return new Vector3d(d[0], d[1], d[2]);
    }
}
