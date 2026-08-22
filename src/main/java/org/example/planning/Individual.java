package org.example.planning;

import java.util.Arrays;
import java.util.Random;

/**
 * Osobnik (rozwiązanie) — zakodowane trasy wszystkich dronów {@code genes[dron][krok]} jako kody
 * ruchów z {@link MoveEncoding} oraz wektor wartości kryteriów. Pola {@code rank} i
 * {@code crowdingDistance} są metadanymi selekcji (NSGA-II / archiwum MOGWO) i nie są kopiowane.
 */
public final class Individual {

    public static final int OBJECTIVE_COUNT = 3;

    public static final int MAKESPAN = 0;

    public static final int ENERGY = 1;

    public static final int RADAR_RISK = 2;

    private final int[][] genes;

    private final double[] objectives = new double[OBJECTIVE_COUNT];

    private int rank = Integer.MAX_VALUE;

    private double crowdingDistance;

    public Individual(int[][] genes) {
        this.genes = genes;
    }

    public static Individual randomIndividual(PlanningProblem problem, Random random) {
        int[][] generatedGenes = new int[problem.droneCount()][problem.maxStepsPerDrone()];
        for (int[] droneGenes : generatedGenes) {
            for (int step = 0; step < droneGenes.length; step++) {
                droneGenes[step] = random.nextInt(MoveEncoding.COUNT);
            }
        }
        return new Individual(generatedGenes);
    }

    public void evaluate(PlanningProblem problem, MultiDroneRouteEvaluator evaluator) {
        double[] result = evaluator.evaluate(problem, genes).objectives();
        System.arraycopy(result, 0, objectives, 0, OBJECTIVE_COUNT);
    }

    /** Głęboka kopia genów i kryteriów (bez metadanych selekcji). */
    public Individual copy() {
        int[][] copiedGenes = new int[genes.length][];
        for (int i = 0; i < genes.length; i++) {
            copiedGenes[i] = genes[i].clone();
        }
        Individual copy = new Individual(copiedGenes);
        System.arraycopy(objectives, 0, copy.objectives, 0, OBJECTIVE_COUNT);
        return copy;
    }

    public int[][] getGenes() {
        return genes;
    }

    /** Wewnętrzna tablica kryteriów (dla wydajności — nie modyfikować). */
    public double[] getObjectives() {
        return objectives;
    }

    public double objective(int index) {
        return objectives[index];
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
        return String.format("Individual[makespan=%.2f energy=%.2f radarRisk=%.2f]",
                objectives[MAKESPAN], objectives[ENERGY], objectives[RADAR_RISK]);
    }

    /** Równość wartości kryteriów (nie genów). */
    public boolean hasSameObjectives(Individual other) {
        return Arrays.equals(objectives, other.objectives);
    }
}
