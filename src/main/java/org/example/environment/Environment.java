package org.example.environment;

import java.awt.geom.Point2D;
import java.util.List;

public class Environment {

    private List<Obstacle> obstacles;

    private WindField windField;

    private double width;

    private double height;

    public void updateEnvironment(double timeStep) {
        windField.update(timeStep);
        obstacles.forEach(o -> o.update(timeStep));
    }

    public boolean isCollision(Point2D point) {
        return obstacles.stream().anyMatch(o -> o.collides(point));
    }

    // getters & setters
}