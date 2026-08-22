package org.example;

import org.example.environment.Vector3d;

/** Dron w warstwie prezentacji: tożsamość + bieżąca pozycja animowana na mapie. */
public final class Drone {

    private final int id;

    private Vector3d position;

    public Drone(int id, Vector3d startPosition) {
        this.id = id;
        this.position = startPosition;
    }

    public int getId() {
        return id;
    }

    public Vector3d getPosition() {
        return position;
    }

    public void setPosition(Vector3d position) {
        this.position = position;
    }
}
