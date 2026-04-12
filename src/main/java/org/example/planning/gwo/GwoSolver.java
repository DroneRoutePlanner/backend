package org.example.planning.gwo;

import org.example.planning.MoveEncoding;
import org.example.planning.MultiDroneRouteEvaluator;
import org.example.planning.PlanningProblem;
import org.example.planning.nsga.Individual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

public final class GwoSolver {

    private static final double INFEASIBLE_BASE = 1e15;

    private static final int COUNT = MoveEncoding.COUNT;

    private final MultiDroneRouteEvaluator evaluator = new MultiDroneRouteEvaluator();

    private final Random random;

    public GwoSolver(long seed) {
        this.random = new Random(seed);
    }

    public Individual run(PlanningProblem problem, int packSize, int iterations) {
        return run(problem, packSize, iterations, null);
    }

    public Individual run(
            PlanningProblem problem,
            int packSize,
            int iterations,
            BiConsumer<Integer, List<Individual>> onIteration) {
        if (packSize < 3) {
            throw new IllegalArgumentException("GWO wymaga co najmniej 3 wilków (α, β, δ)");
        }
        if (iterations < 1) {
            throw new IllegalArgumentException("GWO wymaga co najmniej 1 iteracji");
        }
        int drones = problem.droneCount();
        int steps = problem.maxStepsPerDrone();

        List<double[][]> positions = new ArrayList<>(packSize);
        List<Individual> population = new ArrayList<>(packSize);
        for (int i = 0; i < packSize; i++) {
            double[][] pos = randomPosition(drones, steps);
            positions.add(pos);
            int[][] genes = new int[drones][steps];
            discreteFromContinuous(pos, genes);
            Individual ind = new Individual(genes);
            ind.evaluate(problem, evaluator);
            population.add(ind);
        }

        sortByFitness(population, positions);

        for (int iter = 0; iter < iterations; iter++) {
            double a = 2.0 - iter * (2.0 / iterations);

            double[][] xAlpha = positions.get(0);
            double[][] xBeta = positions.get(1);
            double[][] xDelta = positions.get(2);

            for (int w = 0; w < packSize; w++) {
                double[][] x = positions.get(w);
                for (int d = 0; d < drones; d++) {
                    for (int t = 0; t < steps; t++) {
                        double r1 = random.nextDouble();
                        double r2 = random.nextDouble();
                        double r3 = random.nextDouble();
                        double r4 = random.nextDouble();
                        double r5 = random.nextDouble();
                        double r6 = random.nextDouble();

                        double a1 = 2.0 * a * r1 - a;
                        double a2 = 2.0 * a * r2 - a;
                        double a3 = 2.0 * a * r3 - a;
                        double c1 = 2.0 * r4;
                        double c2 = 2.0 * r5;
                        double c3 = 2.0 * r6;

                        double dAlpha = Math.abs(c1 * xAlpha[d][t] - x[d][t]);
                        double dBeta = Math.abs(c2 * xBeta[d][t] - x[d][t]);
                        double dDelta = Math.abs(c3 * xDelta[d][t] - x[d][t]);

                        double x1 = xAlpha[d][t] - a1 * dAlpha;
                        double x2 = xBeta[d][t] - a2 * dBeta;
                        double x3 = xDelta[d][t] - a3 * dDelta;
                        x[d][t] = clampGene((x1 + x2 + x3) / 3.0);
                    }
                }
                discreteFromContinuous(x, population.get(w).getGenes());
                population.get(w).evaluate(problem, evaluator);
            }

            sortByFitness(population, positions);

            if (onIteration != null) {
                onIteration.accept(iter, new ArrayList<>(population));
            }
        }

        return population.get(0).copy();
    }

    public static double scalarFitness(Individual ind) {
        if (!ind.isFeasible()) {
            return INFEASIBLE_BASE + ind.getConstraintViolation();
        }
        double[] o = ind.getObjectives();
        return o[0] + 0.02 * o[1] + o[2];
    }

    private static void sortByFitness(List<Individual> population, List<double[][]> positions) {
        Integer[] idx = new Integer[population.size()];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, Comparator.comparingDouble(i -> scalarFitness(population.get(i))));

        List<Individual> sortedInd = new ArrayList<>(population.size());
        List<double[][]> sortedPos = new ArrayList<>(positions.size());
        for (int i : idx) {
            sortedInd.add(population.get(i));
            sortedPos.add(positions.get(i));
        }
        population.clear();
        positions.clear();
        population.addAll(sortedInd);
        positions.addAll(sortedPos);
    }

    private double[][] randomPosition(int drones, int steps) {
        double[][] pos = new double[drones][steps];
        for (int d = 0; d < drones; d++) {
            for (int t = 0; t < steps; t++) {
                pos[d][t] = random.nextDouble() * (COUNT - 1);
            }
        }
        return pos;
    }

    private static void discreteFromContinuous(double[][] pos, int[][] genes) {
        for (int d = 0; d < pos.length; d++) {
            for (int t = 0; t < pos[d].length; t++) {
                int v = (int) Math.round(pos[d][t]);
                genes[d][t] = clampInt(v);
            }
        }
    }

    private static double clampGene(double v) {
        if (v < 0) {
            return 0;
        }
        if (v > COUNT - 1) {
            return COUNT - 1;
        }
        return v;
    }

    private static int clampInt(int v) {
        if (v < 0) {
            return 0;
        }
        if (v >= COUNT) {
            return COUNT - 1;
        }
        return v;
    }
}
