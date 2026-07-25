package org.example.planning.gwo;

import org.example.planning.Individual;
import org.example.planning.MoveEncoding;
import org.example.planning.MultiDroneRouteEvaluator;
import org.example.planning.PlanningProblem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

public final class GwoSolver {

    private static final double INFEASIBLE_BASE = 1e7;
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

        List<Individual> population = new ArrayList<>(packSize);
        for (int i = 0; i < packSize; i++) {
            Individual ind = Individual.randomIndividual(problem, random);
            ind.evaluate(problem, evaluator);
            population.add(ind);
        }

        sortByFitness(population);

        for (int iter = 0; iter < iterations; iter++) {
            double a = 2.0 - iter * (2.0 / iterations);

            int[][] gAlpha = cloneGenes(population.get(0).getGenes(), drones, steps);
            int[][] gBeta = cloneGenes(population.get(1).getGenes(), drones, steps);
            int[][] gDelta = cloneGenes(population.get(2).getGenes(), drones, steps);

            for (int w = 0; w < packSize; w++) {
                int[][] currentGenes = population.get(w).getGenes();

                for (int d = 0; d < drones; d++) {
                    for (int t = 0; t < steps; t++) {

                        double r1 = random.nextDouble();
                        double r2 = random.nextDouble();
                        double r3 = random.nextDouble();

                        double A1 = 2.0 * a * r1 - a;
                        double A2 = 2.0 * a * r2 - a;
                        double A3 = 2.0 * a * r3 - a;

                        int x1 = (Math.abs(A1) < 1.0) ? gAlpha[d][t]
                                : (random.nextBoolean() ? currentGenes[d][t] : random.nextInt(COUNT));
                        int x2 = (Math.abs(A2) < 1.0) ? gBeta[d][t]
                                : (random.nextBoolean() ? currentGenes[d][t] : random.nextInt(COUNT));
                        int x3 = (Math.abs(A3) < 1.0) ? gDelta[d][t]
                                : (random.nextBoolean() ? currentGenes[d][t] : random.nextInt(COUNT));

                        int nextMove;
                        if (x1 == x2 || x1 == x3) {
                            nextMove = x1;
                        } else if (x2 == x3) {
                            nextMove = x2;
                        } else {
                            double rand = random.nextDouble();
                            if (rand < 0.5)
                                nextMove = x1;
                            else if (rand < 0.8)
                                nextMove = x2;
                            else
                                nextMove = x3;
                        }

                        if (random.nextDouble() < (a * 0.05)) {
                            nextMove = random.nextInt(COUNT);
                        }

                        currentGenes[d][t] = nextMove;
                    }
                }
                population.get(w).evaluate(problem, evaluator);
            }

            sortByFitness(population);

            if (iter % 10 == 0 || iter == iterations - 1) {
                printIterationProgress("GWO", iter, population);
            }

            if (onIteration != null) {
                onIteration.accept(iter, new ArrayList<>(population));
            }
        }

        return population.get(0).copy();
    }

    public static double scalarFitness(Individual ind) {
        double fitness = 0.0;
        if (!ind.isFeasible()) {
            fitness += INFEASIBLE_BASE + ind.getConstraintViolation();
        }
        double[] o = ind.getObjectives();
        fitness += o[0] + 0.02 * o[1] + o[2];
        return fitness;
    }

    private static void sortByFitness(List<Individual> population) {
        population.sort(Comparator.comparingDouble(GwoSolver::scalarFitness));
    }

    private static int[][] cloneGenes(int[][] source, int drones, int steps) {
        int[][] target = new int[drones][steps];
        for (int d = 0; d < drones; d++) {
            System.arraycopy(source[d], 0, target[d], 0, steps);
        }
        return target;
    }

    public void printIterationProgress(String title, int iteration, List<Individual> population) {
        if (population == null || population.isEmpty()) {
            System.out.println("--- Iteracja " + iteration + ": Populacja jest pusta ---");
            return;
        }

        // Dynamiczny nagłówek informujący o bieżącej iteracji
        String headerText = String.format(" ITERACJA %d [%s] ", iteration, title.toUpperCase());
        System.out.println("\n=======================" + headerText + "=======================");
        System.out.printf("%-5s | %-5s | %-13s | %-12s | %-12s | %-12s%n",
                "Wilk", "Rank", "Crowding Dist", "Kryterium 0", "Kryterium 1", "Kryterium 2");
        System.out.println("-----------------------------------------------------------------------------");

        // W trakcie iteracji drukujemy tylko TOP 3 (Alfa, Beta, Delta), żeby nie
        // zapchać konsoli
        int limit = Math.min(3, population.size());
        String[] roles = { "Alpha", "Beta", "Delta" };

        for (int i = 0; i < limit; i++) {
            Individual ind = population.get(i);
            double[] objectives = ind.getObjectives();

            System.out.printf("%-5s | %-5d | %-13.4f | %-12.4f | %-12.4f | %-12.4f%n",
                    roles[i],
                    ind.getRank(),
                    ind.getCrowdingDistance(),
                    objectives[0],
                    objectives[1],
                    objectives[2]);
        }

        // Jeśli populacja jest większa, dodajemy informację o reszcie stada
        if (population.size() > 3) {
            System.out.println("-----------------------------------------------------------------------------");
            System.out.printf("... oraz %d pozostałych wilków w stadzie.%n", (population.size() - 3));
        }

        // Dodatkowe szybkie statystyki pomocne przy debugowaniu zbieżności algorytmu
        long feasibleCount = population.stream().filter(Individual::isFeasible).count();
        System.out.println("-----------------------------------------------------------------------------");
        System.out.printf("Rozwiązania dopuszczalne: %d/%d | Najlepszy Scalar Fitness: %.4f%n",
                feasibleCount, population.size(), GwoSolver.scalarFitness(population.get(0)));
        System.out.println("=============================================================================\n");
    }
}