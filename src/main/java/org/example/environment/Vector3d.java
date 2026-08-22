package org.example.environment;

import java.util.Objects;

/** Niemutowalny wektor/punkt siatki 3D o współrzędnych całkowitych. */
public final class Vector3d {

    private final int x;

    private final int y;

    private final int z;

    public Vector3d(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3d add(Vector3d other) {
        return new Vector3d(x + other.x, y + other.y, z + other.z);
    }

    public Vector3d subtract(Vector3d other) {
        return new Vector3d(x - other.x, y - other.y, z - other.z);
    }

    /** Odległość Czebyszewa — minimalna liczba ruchów w siatce 26-spójnej. */
    public int chebyshevDistance(Vector3d other) {
        return Math.max(Math.abs(x - other.x), Math.max(Math.abs(y - other.y), Math.abs(z - other.z)));
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vector3d vector)) {
            return false;
        }
        return x == vector.x && y == vector.y && z == vector.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + z + ")";
    }
}
