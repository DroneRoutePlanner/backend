package org.example;

import org.example.environment.Vector3d;

public class Drone {

    private Vector3d position;

    public Drone(Vector3d startPosition) {
        this.position = startPosition;
    }

    public void move(Direction direction) {
        switch (direction) {
            case FORWARD -> position = position.add(new Vector3d(1, 0, 0));
            case BACKWARD -> position = position.add(new Vector3d(-1, 0, 0));
            case LEFT -> position = position.add(new Vector3d(0, -1, 0));
            case RIGHT -> position = position.add(new Vector3d(0, 1, 0));
            case UP -> position = position.add(new Vector3d(0, 0, 1));
            case DOWN -> position = position.add(new Vector3d(0, 0, -1));
        }
    }

    public Vector3d getPosition() {
        return position;
    }
}
