package org.example.planning.model;


public final class RadarStation {

    private final double baseX;

    private final double baseY;

    private final double baseZ;

    private final double driftVx;

    private final double driftVy;

    private final double influenceRadius;

    private final double strength;

    public RadarStation(
            double baseX,
            double baseY,
            double baseZ,
            double driftVx,
            double driftVy,
            double influenceRadius,
            double strength
    ) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.baseZ = baseZ;
        this.driftVx = driftVx;
        this.driftVy = driftVy;
        this.influenceRadius = influenceRadius;
        this.strength = strength;
    }

    public double[] positionAt(double time) {
        return new double[]{
                baseX + driftVx * time,
                baseY + driftVy * time,
                baseZ
        };
    }

    public double getInfluenceRadius() {
        return influenceRadius;
    }
   
    public double detectionRisk(double x, double y, double z, double time) {
        double[] p = positionAt(time);
        double dx = x - p[0];
        double dy = y - p[1];
        double dz = z - p[2];
        double dist2 = dx * dx + dy * dy + dz * dz;
        double r2 = influenceRadius * influenceRadius;
        if (dist2 > r2) {
            return 0.0;
        }
        return strength / (dist2 + 1.0);
    }
}
