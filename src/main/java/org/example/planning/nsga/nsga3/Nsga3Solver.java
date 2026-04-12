package org.example.planning.nsga.nsga3;

import org.example.planning.MultiDroneRouteEvaluator;
import org.example.planning.PlanningProblem;
import org.example.planning.nsga.CrowdingDistance;
import org.example.planning.nsga.Individual;
import org.example.planning.nsga.NonDominatedSort;
import org.example.planning.nsga.NsgaVariation;
import org.example.planning.nsga.ReferenceDirections;

import java.util.ArrayList;
import java.util.Comparator;
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
        double[][] refDirs = ReferenceDirections.forPopulationSize(populationSize);

        List<Individual> population = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            Individual ind = Individual.randomIndividual(problem, random);
            ind.evaluate(problem, evaluator);
            population.add(ind);
        }
        assignRankAndCrowding(population);

        for (int g = 0; g < generations; g++) {
            List<Individual> offspring = makeOffspring(problem, population, populationSize);
            List<Individual> combined = new ArrayList<>(population.size() + offspring.size());
            combined.addAll(population);
            combined.addAll(offspring);
            population = environmentalSelection(combined, populationSize, refDirs);
            if (onGeneration != null) {
                onGeneration.accept(g, new ArrayList<>(population));
            }
        }

        List<List<Individual>> fronts = NonDominatedSort.sort(population);
        return new ArrayList<>(fronts.get(0));
    }

    private List<Individual> makeOffspring(PlanningProblem problem, List<Individual> population, int targetSize) {
        List<Individual> offspring = new ArrayList<>();
        while (offspring.size() < targetSize) {
            Individual p1 = binaryTournament(population);
            Individual p2 = binaryTournament(population);
            Individual c1 = p1.copy();
            Individual c2 = p2.copy();
            if (random.nextDouble() < 0.9) {
                NsgaVariation.crossover(random, c1, c2);
            }
            NsgaVariation.mutate(random, c1, 0.12);
            NsgaVariation.mutate(random, c2, 0.12);
            c1.evaluate(problem, evaluator);
            c2.evaluate(problem, evaluator);
            offspring.add(c1);
            offspring.add(c2);
        }
        while (offspring.size() > targetSize) {
            offspring.remove(offspring.size() - 1);
        }
        return offspring;
    }

    private Individual binaryTournament(List<Individual> pop) {
        Individual a = pop.get(random.nextInt(pop.size()));
        Individual b = pop.get(random.nextInt(pop.size()));
        if (better(a, b)) {
            return a;
        }
        return b;
    }

    private static boolean better(Individual a, Individual b) {
        if (a.getRank() != b.getRank()) {
            return a.getRank() < b.getRank();
        }
        return a.getCrowdingDistance() > b.getCrowdingDistance();
    }

    private List<Individual> environmentalSelection(List<Individual> combined, int n, double[][] refDirs) {
        List<List<Individual>> fronts = NonDominatedSort.sort(combined);
        List<Individual> next = new ArrayList<>(n);
        int frontIndex = 0;
        while (frontIndex < fronts.size() && next.size() + fronts.get(frontIndex).size() <= n) {
            CrowdingDistance.assign(fronts.get(frontIndex));
            next.addAll(fronts.get(frontIndex));
            frontIndex++;
        }
        if (next.size() < n && frontIndex < fronts.size()) {
            List<Individual> last = new ArrayList<>(fronts.get(frontIndex));
            int need = n - next.size();
            boolean allFeasible = last.stream().allMatch(Individual::isFeasible);
            if (allFeasible) {
                nichingFill(next, last, need, refDirs);
            } else {
                CrowdingDistance.assign(last);
                last.sort(Comparator.comparingDouble(Individual::getCrowdingDistance).reversed());
                for (int i = 0; i < need; i++) {
                    next.add(last.get(i));
                }
            }
        }
        assignRankAndCrowding(next);
        return next;
    }

    /**
     * Dobór z {@code lastFront} (wszystkie dopuszczalne) metodą nisz (Deb & Jain).
     */
    private void nichingFill(List<Individual> next, List<Individual> lastFront, int need, double[][] refDirs) {
        List<Individual> union = new ArrayList<>(next.size() + lastFront.size());
        union.addAll(next);
        union.addAll(lastFront);
        double[] ideal = new double[Individual.OBJECTIVE_COUNT];
        double[] nadir = new double[Individual.OBJECTIVE_COUNT];
        boundsFeasible(union, ideal, nadir);

        int[] rho = new int[refDirs.length];
        for (Individual s : next) {
            if (s.isFeasible()) {
                double[] z = normalizedObjectives(s, ideal, nadir);
                rho[associate(z, refDirs)]++;
            }
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
        if (picked < need && !lastFront.isEmpty()) {
            CrowdingDistance.assign(lastFront);
            lastFront.sort(Comparator.comparingDouble(Individual::getCrowdingDistance).reversed());
            int i = 0;
            while (picked < need && i < lastFront.size()) {
                next.add(lastFront.get(i));
                i++;
                picked++;
            }
        }
    }

    private static void boundsFeasible(List<Individual> pool, double[] ideal, double[] nadir) {
        for (int m = 0; m < Individual.OBJECTIVE_COUNT; m++) {
            ideal[m] = Double.POSITIVE_INFINITY;
            nadir[m] = Double.NEGATIVE_INFINITY;
        }
        int feasibleCount = 0;
        for (Individual ind : pool) {
            if (!ind.isFeasible()) {
                continue;
            }
            feasibleCount++;
            double[] o = ind.getObjectives();
            for (int m = 0; m < Individual.OBJECTIVE_COUNT; m++) {
                ideal[m] = Math.min(ideal[m], o[m]);
                nadir[m] = Math.max(nadir[m], o[m]);
            }
        }
        if (feasibleCount == 0) {
            for (Individual ind : pool) {
                double[] o = ind.getObjectives();
                for (int m = 0; m < Individual.OBJECTIVE_COUNT; m++) {
                    ideal[m] = Math.min(ideal[m], o[m]);
                    nadir[m] = Math.max(nadir[m], o[m]);
                }
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

    private void assignRankAndCrowding(List<Individual> population) {
        List<List<Individual>> fronts = NonDominatedSort.sort(population);
        for (int i = 0; i < fronts.size(); i++) {
            CrowdingDistance.assign(fronts.get(i));
            for (Individual ind : fronts.get(i)) {
                ind.setRank(i);
            }
        }
    }
}
