package org.example.planning;

import org.example.environment.Vector3d;

public final class MoveEncoding {

    public static final int COUNT = 6;

    private MoveEncoding() {
    }

    public static Vector3d delta(int code) {
        return switch (code) {
            case 0 -> new Vector3d(1, 0, 0);
            case 1 -> new Vector3d(-1, 0, 0);
            case 2 -> new Vector3d(0, 1, 0);
            case 3 -> new Vector3d(0, -1, 0);
            case 4 -> new Vector3d(0, 0, 1);
            case 5 -> new Vector3d(0, 0, -1);
            default -> new Vector3d(0, 0, 0);
        };
    }
}
