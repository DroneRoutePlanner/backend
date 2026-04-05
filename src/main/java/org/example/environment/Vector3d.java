package org.example.environment;

import java.util.Objects;

public class Vector3d {

    private final int x;

    private final int y;

    private final int z;

    public Vector3d(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String toString() {
        return "(" + this.x + "," + this.y + "," + this.z + ")";
    }

    public Vector3d add(Vector3d position) {
        return new Vector3d(this.x + position.x, this.y + position.y, this.z + position.z);
    }

    public Vector3d subtract(Vector3d position) {
        return new Vector3d(this.x - position.x, this.y - position.y, this.z - position.z);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Vector3d vector)) return false;

        return x == vector.x && y == vector.y && z == vector.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
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
}