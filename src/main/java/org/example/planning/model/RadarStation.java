package org.example.planning.model;

public final class RadarStation {

    private final double x;

    private final double y;

    private final double z;

    private final double influenceRadius;

    private final double strength;

    public RadarStation(
            double x,
            double y,
            double z,
            double influenceRadius,
            double strength
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.influenceRadius = influenceRadius;
        this.strength = strength;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double getInfluenceRadius() {
        return influenceRadius;
    }

    public double detectionRisk(double px, double py, double pz) {
        double dx = px - x;
        double dy = py - y;
        double dz = pz - z;
        double dist2 = dx * dx + dy * dy + dz * dz;
        double r2 = influenceRadius * influenceRadius;
        if (dist2 > r2) {
            return 0.0;
        }
        return strength / (dist2 + 1.0);
    }
}
