package org.example.environment;

public class WindField {
    private double windX;
    private double windY;

    public void update(double timeStep) {
        windX += (Math.random() - 0.5) * 0.1;
        windY += (Math.random() - 0.5) * 0.1;
    }

    public double[] getWind() {
        return new double[]{windX, windY};
    }
}
