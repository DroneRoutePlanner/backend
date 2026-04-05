package org.example.environment;

import java.awt.geom.Point2D;

public abstract class Obstacle {
    protected Point2D position;
    public abstract void update(double timeStep);
    public abstract boolean collides(Point2D point);
}