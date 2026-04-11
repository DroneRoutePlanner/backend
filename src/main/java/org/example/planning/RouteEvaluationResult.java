package org.example.planning;

public final class RouteEvaluationResult {

    private final double makespan;

    private final double totalEnergy;

    private final double totalRadarRisk;

    private final double constraintViolation;

    private final boolean feasible;

    public RouteEvaluationResult(
            double makespan,
            double totalEnergy,
            double totalRadarRisk,
            double constraintViolation,
            boolean feasible
    ) {
        this.makespan = makespan;
        this.totalEnergy = totalEnergy;
        this.totalRadarRisk = totalRadarRisk;
        this.constraintViolation = constraintViolation;
        this.feasible = feasible;
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

    public double getConstraintViolation() {
        return constraintViolation;
    }

    public boolean isFeasible() {
        return feasible;
    }

    public double[] objectivesMinimize() {
        return new double[]{makespan, totalEnergy, totalRadarRisk};
    }
}
