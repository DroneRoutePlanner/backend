package org.example.environment;

public final class WindField {

    private final double baseX;

    private final double baseY;

    private double windX;

    private double windY;

    public WindField(double baseX, double baseY) {
        this.baseX = baseX;
        this.baseY = baseY;
        reset();
    }

    public void reset() {
        windX = baseX;
        windY = baseY;
    }

    public void advanceTick(int tick) {
        windX = baseX + 0.08 * Math.sin(tick * 0.12);
        windY = baseY + 0.08 * Math.cos(tick * 0.11);
    }

    public double getWindX() {
        return windX;
    }

    public double getWindY() {
        return windY;
    }

    public double[] getWind() {
        return new double[] { windX, windY };
    }
}
