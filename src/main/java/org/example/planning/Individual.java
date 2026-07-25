package org.example.planning;

import java.util.Arrays;
import java.util.Random;

public final class Individual {

    public static final int OBJECTIVE_COUNT = 3;

    private final int[][] genes;

    private final double[] objectives;

    private int rank;

    private double crowdingDistance;

    public Individual(int[][] genes) {
        this.genes = genes;
        this.objectives = new double[OBJECTIVE_COUNT];
        this.rank = Integer.MAX_VALUE;
        this.crowdingDistance = 0.0;
    }

    public static Individual randomIndividual(PlanningProblem problem, Random rnd) {
        int droneCount = problem.droneCount();
        int maxSteps = problem.maxStepsPerDrone();
        int[][] generatedGenes = new int[droneCount][maxSteps];
        for (int i = 0; i < droneCount; i++) {
            for (int j = 0; j < maxSteps; j++) {
                generatedGenes[i][j] = rnd.nextInt(MoveEncoding.COUNT);
            }
        }
        return new Individual(generatedGenes);
    }

    public void evaluate(PlanningProblem problem, MultiDroneRouteEvaluator evaluator) {
        var result = evaluator.evaluate(problem, genes);
        objectives[0] = result.getMakespan();
        objectives[1] = result.getTotalEnergy();
        objectives[2] = result.getTotalRadarRisk();
    }

    public Individual copy() {
        int[][] g = new int[genes.length][];
        for (int i = 0; i < genes.length; i++) {
            g[i] = Arrays.copyOf(genes[i], genes[i].length);
        }
        Individual c = new Individual(g);
        System.arraycopy(objectives, 0, c.objectives, 0, OBJECTIVE_COUNT);
        return c;
    }

    public int[][] getGenes() {
        return genes;
    }

    public double[] getObjectives() {
        return objectives;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public double getCrowdingDistance() {
        return crowdingDistance;
    }

    public void setCrowdingDistance(double crowdingDistance) {
        this.crowdingDistance = crowdingDistance;
    }

    @Override
    public String toString() {
        return String.format(
                "Individual[ makespan=%.2f energy=%.2f radarRisk=%.2f ]",
                objectives[0], objectives[1], objectives[2]);
    }
}
