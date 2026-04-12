package org.example;

import org.example.environment.Vector3d;

public class Drone {

    private final int id;

    private Vector3d position;

    private final double energyBudget;

    public Drone(Vector3d startPosition) {
        this(0, startPosition, 200.0);
    }

    public Drone(int id, Vector3d startPosition, double energyBudget) {
        this.id = id;
        this.position = startPosition;
        this.energyBudget = energyBudget;
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

    public int getId() {
        return id;
    }

    public Vector3d getPosition() {
        return position;
    }

    public void setPosition(Vector3d position) {
        this.position = position;
    }

    public double getEnergyBudget() {
        return energyBudget;
    }
}
