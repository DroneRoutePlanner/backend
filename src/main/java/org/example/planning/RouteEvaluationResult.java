package org.example.planning;

public final class RouteEvaluationResult {

    private final double makespan;

    private final double totalEnergy;

    private final double totalRadarRisk;

    public RouteEvaluationResult(
            double makespan,
            double totalEnergy,
            double totalRadarRisk
    ) {
        this.makespan = makespan;
        this.totalEnergy = totalEnergy;
        this.totalRadarRisk = totalRadarRisk;
    }

    public double getMakespan() {
        return makespan;
    }

    public double getTotalEnergy() {
        return totalEnergy;
    }

    public double getTotalRadarRisk() {
        return totalRadarRisk;
    }

    public double[] objectivesMinimize() {
        return new double[]{makespan, totalEnergy, totalRadarRisk};
    }
}
