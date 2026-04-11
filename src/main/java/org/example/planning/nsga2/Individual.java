package org.example.planning.nsga2;

import org.example.planning.MoveEncoding;
import org.example.planning.MultiDroneRouteEvaluator;
import org.example.planning.PlanningProblem;

import java.util.Arrays;

public final class Individual {

    private final int[][] genes;

    private final double[] objectives;

    private double constraintViolation;

    private boolean feasible;

    private int rank;

    private double crowdingDistance;

    public Individual(int[][] genes) {
        this.genes = genes;
        this.objectives = new double[3];
        this.rank = Integer.MAX_VALUE;
        this.crowdingDistance = 0.0;
    }

    public static Individual randomIndividual(PlanningProblem problem, java.util.Random rnd) {
        int d = problem.droneCount();
        int t = problem.maxStepsPerDrone();
        int[][] g = new int[d][t];
        for (int i = 0; i < d; i++) {
            for (int j = 0; j < t; j++) {
                g[i][j] = rnd.nextInt(MoveEncoding.COUNT);
            }
        }
        return new Individual(g);
    }

    public void evaluate(PlanningProblem problem, MultiDroneRouteEvaluator evaluator) {
        var r = evaluator.evaluate(problem, genes);
        objectives[0] = r.getMakespan();
        objectives[1] = r.getTotalEnergy();
        objectives[2] = r.getTotalRadarRisk();
        constraintViolation = r.getConstraintViolation();
        feasible = r.isFeasible();
    }

    public Individual copy() {
        int[][] g = new int[genes.length][];
        for (int i = 0; i < genes.length; i++) {
            g[i] = Arrays.copyOf(genes[i], genes[i].length);
        }
        return new Individual(g);
    }

    public int[][] getGenes() {
        return genes;
    }

    public double[] getObjectives() {
        return objectives;
    }

    public double getConstraintViolation() {
        return constraintViolation;
    }

    public boolean isFeasible() {
        return feasible;
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
                "Individual[ makespan=%.2f energy=%.2f radarRisk=%.2f feasible=%s cv=%.2f ]",
                objectives[0], objectives[1], objectives[2], feasible, constraintViolation
        );
    }
}
