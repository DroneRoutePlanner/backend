package org.example.planning.nsga.nsga3;

import org.example.planning.Individual;
import org.example.planning.MultiDroneRouteEvaluator;
import org.example.planning.PlanningProblem;
import org.example.planning.nsga.NonDominatedSorting;
import org.example.planning.nsga.NsgaVariation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

public final class Nsga3Solver {

    private static final double EPS = 1e-12;

    private final MultiDroneRouteEvaluator evaluator = new MultiDroneRouteEvaluator();

    private final Random random;

    public Nsga3Solver(long seed) {
        this.random = new Random(seed);
    }

    public List<Individual> run(PlanningProblem problem, int populationSize, int generations) {
        return run(problem, populationSize, generations, null);
    }

    public List<Individual> run(
            PlanningProblem problem,
            int populationSize,
            int generations,
            BiConsumer<Integer, List<Individual>> onGeneration) {
        double[][] referenceDirections = ReferenceDirections.forPopulationSize(populationSize);

        List<Individual> currentPopulation = new ArrayList<>(populationSize);

        for (int i = 0; i < populationSize; i++) {
            Individual individual = Individual.randomIndividual(problem, random);
            individual.evaluate(problem, evaluator);
            currentPopulation.add(individual);
        }

        assignRanks(currentPopulation);
        printPopulation(currentPopulation, "Populacja Początkowa");

        for (int generation = 0; generation < generations; generation++) {
            List<Individual> offspring = makeOffspring(problem, currentPopulation, populationSize);
            List<Individual> combined = new ArrayList<>(currentPopulation.size() + offspring.size());
            combined.addAll(currentPopulation);
            combined.addAll(offspring);

            currentPopulation = environmentalSelection(combined, populationSize, referenceDirections);

            if (onGeneration != null) {
                onGeneration.accept(generation, new ArrayList<>(currentPopulation));
            }

            printPopulation(currentPopulation, "Populacja Późniejsza");
        }

        List<List<Individual>> fronts = NonDominatedSorting.sort(currentPopulation);
        return new ArrayList<>(fronts.get(0));
    }

    private List<Individual> makeOffspring(PlanningProblem problem, List<Individual> population, int targetSize) {
        List<Individual> offspring = new ArrayList<>();

        while (offspring.size() < targetSize) {
            Individual parent1 = binaryTournament(population);
            Individual parent2 = binaryTournament(population);
            Individual child1 = parent1.copy();
            Individual child2 = parent2.copy();

            if (random.nextDouble() < 0.9) {
                NsgaVariation.crossover(random, child1, child2);
            }

            NsgaVariation.mutate(random, child1, 0.12);
            NsgaVariation.mutate(random, child2, 0.12);
            child1.evaluate(problem, evaluator);
            child2.evaluate(problem, evaluator);
            offspring.add(child1);
            offspring.add(child2);
        }

        while (offspring.size() > targetSize) {
            offspring.remove(offspring.size() - 1);
        }

        return offspring;
    }

    private Individual binaryTournament(List<Individual> population) {
        Individual candidateA = population.get(random.nextInt(population.size()));
        Individual candidateB = population.get(random.nextInt(population.size()));
        if (candidateA.getRank() != candidateB.getRank()) {
            return candidateA.getRank() < candidateB.getRank() ? candidateA : candidateB;
        }
        return random.nextBoolean() ? candidateA : candidateB;
    }

    private List<Individual> environmentalSelection(List<Individual> combinedPopulation, int targetPopulationSize,
            double[][] referenceDirections) {
        List<List<Individual>> fronts = NonDominatedSorting.sort(combinedPopulation);
        List<Individual> selectedPopulation = new ArrayList<>(targetPopulationSize);
        int frontIndex = 0;

        while (frontIndex < fronts.size()
                && selectedPopulation.size() + fronts.get(frontIndex).size() <= targetPopulationSize) {
            selectedPopulation.addAll(fronts.get(frontIndex));
            frontIndex++;
        }

        if (selectedPopulation.size() < targetPopulationSize && frontIndex < fronts.size()) {
            List<Individual> last = new ArrayList<>(fronts.get(frontIndex));
            int individualsNeededSize = targetPopulationSize - selectedPopulation.size();
            nichingFill(selectedPopulation, last, individualsNeededSize, referenceDirections);
        }

        assignRanks(selectedPopulation);
        return selectedPopulation;
    }

    /**
     * Dobór z {@code lastFront} metodą nisz (Deb & Jain).
     */
    private void nichingFill(List<Individual> next, List<Individual> lastFront, int need, double[][] refDirs) {
        List<Individual> union = new ArrayList<>(next.size() + lastFront.size());
        union.addAll(next);
        union.addAll(lastFront);
        double[] ideal = new double[Individual.OBJECTIVE_COUNT];
        double[] nadir = new double[Individual.OBJECTIVE_COUNT];
        computeObjectiveBounds(union, ideal, nadir);

        int[] rho = new int[refDirs.length];
        for (Individual s : next) {
            double[] z = normalizedObjectives(s, ideal, nadir);
            rho[associate(z, refDirs)]++;
        }

        int picked = 0;
        while (picked < need && !lastFront.isEmpty()) {
            int jStar = argMinRho(rho);
            List<Individual> assoc = new ArrayList<>();
            for (Individual x : lastFront) {
                double[] z = normalizedObjectives(x, ideal, nadir);
                if (associate(z, refDirs) == jStar) {
                    assoc.add(x);
                }
            }
            if (assoc.isEmpty()) {
                rho[jStar] = Integer.MAX_VALUE / 4;
                continue;
            }
            Individual best = null;
            double bestDist = Double.POSITIVE_INFINITY;
            for (Individual x : assoc) {
                double[] z = normalizedObjectives(x, ideal, nadir);
                double d = perpendicularDistance(z, refDirs[jStar]);
                if (d < bestDist) {
                    bestDist = d;
                    best = x;
                }
            }
            next.add(best);
            lastFront.remove(best);
            rho[jStar]++;
            picked++;
        }

        while (picked < need && !lastFront.isEmpty()) {
            int idx = random.nextInt(lastFront.size());
            next.add(lastFront.remove(idx));
            picked++;
        }
    }

    private static void computeObjectiveBounds(List<Individual> pool, double[] ideal, double[] nadir) {
        for (int m = 0; m < Individual.OBJECTIVE_COUNT; m++) {
            ideal[m] = Double.POSITIVE_INFINITY;
            nadir[m] = Double.NEGATIVE_INFINITY;
        }
        for (Individual ind : pool) {
            double[] o = ind.getObjectives();
            for (int m = 0; m < Individual.OBJECTIVE_COUNT; m++) {
                ideal[m] = Math.min(ideal[m], o[m]);
                nadir[m] = Math.max(nadir[m], o[m]);
            }
        }
    }

    private static double[] normalizedObjectives(Individual ind, double[] ideal, double[] nadir) {
        double[] o = ind.getObjectives();
        double[] z = new double[Individual.OBJECTIVE_COUNT];
        for (int m = 0; m < Individual.OBJECTIVE_COUNT; m++) {
            double den = nadir[m] - ideal[m];
            if (den < EPS) {
                z[m] = 0;
            } else {
                z[m] = (o[m] - ideal[m]) / den;
            }
        }
        return z;
    }

    private static double perpendicularDistance(double[] f, double[] w) {
        double wSq = w[0] * w[0] + w[1] * w[1] + w[2] * w[2];
        if (wSq < EPS) {
            return Double.POSITIVE_INFINITY;
        }
        double t = (f[0] * w[0] + f[1] * w[1] + f[2] * w[2]) / wSq;
        if (t < 0) {
            t = 0;
        }
        double px = t * w[0];
        double py = t * w[1];
        double pz = t * w[2];
        double dx = f[0] - px;
        double dy = f[1] - py;
        double dz = f[2] - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int associate(double[] fNorm, double[][] refDirs) {
        int best = 0;
        double bestD = Double.POSITIVE_INFINITY;
        for (int j = 0; j < refDirs.length; j++) {
            double d = perpendicularDistance(fNorm, refDirs[j]);
            if (d < bestD) {
                bestD = d;
                best = j;
            }
        }
        return best;
    }

    private static int argMinRho(int[] rho) {
        int j = 0;
        for (int k = 1; k < rho.length; k++) {
            if (rho[k] < rho[j]) {
                j = k;
            }
        }
        return j;
    }

    // rozstrzyganie losowe
    private void assignRanks(List<Individual> population) {
        List<List<Individual>> fronts = NonDominatedSorting.sort(population);
        for (int i = 0; i < fronts.size(); i++) {
            for (Individual ind : fronts.get(i)) {
                ind.setRank(i);
            }
        }
    }

    public static void printPopulation(List<Individual> population, String title) {
        if (population == null || population.isEmpty()) {
            System.out.println("\n[INFO] Populacja jest pusta lub nie istnieje.");
            return;
        }

        // Dynamicznie centrowany nagłówek dla estetyki
        System.out.println("\n=========================== " + title.toUpperCase() + " ===========================");
        System.out.printf("%-5s | %-5s | %-12s | %-12s | %-12s%n",
                "Nr", "Rank", "Kryterium 0", "Kryterium 1", "Kryterium 2");
        System.out.println("-----------------------------------------------------------------------------");

        for (int i = 0; i < population.size(); i++) {
            Individual ind = population.get(i);
            double[] objectives = ind.getObjectives();

            System.out.printf("#%-4d | %-5d | %-12.4f | %-12.4f | %-12.4f%n",
                    (i + 1),
                    ind.getRank(),
                    objectives[0],
                    objectives[1],
                    objectives[2]);
        }
        System.out.println("=============================================================================\n");
    }
}
