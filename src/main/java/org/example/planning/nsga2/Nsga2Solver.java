package org.example.planning.nsga2;

import org.example.planning.MoveEncoding;
import org.example.planning.MultiDroneRouteEvaluator;
import org.example.planning.PlanningProblem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

public final class Nsga2Solver {

    private static final double CV_EPS = 1e-9;

    private final MultiDroneRouteEvaluator evaluator = new MultiDroneRouteEvaluator();

    private final Random random;

    public Nsga2Solver(long seed) {
        this.random = new Random(seed);
    }

    public List<Individual> run(PlanningProblem problem, int populationSize, int generations) {
        return run(problem, populationSize, generations, null);
    }

    public List<Individual> run(
            PlanningProblem problem,
            int populationSize,
            int generations,
            BiConsumer<Integer, List<Individual>> onGeneration
    ) {
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
            population = environmentalSelection(combined, populationSize);
            if (onGeneration != null) {
                onGeneration.accept(g, new ArrayList<>(population));
            }
        }

        List<List<Individual>> fronts = fastNonDominatedSort(population);
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
                crossover(c1, c2);
            }
            mutate(c1, 0.12);
            mutate(c2, 0.12);
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

    private void crossover(Individual c1, Individual c2) {
        int[][] g1 = c1.getGenes();
        int[][] g2 = c2.getGenes();
        int drones = g1.length;
        for (int d = 0; d < drones; d++) {
            if (random.nextBoolean()) {
                continue;
            }
            int len = g1[d].length;
            if (len < 2) {
                continue;
            }
            int point = 1 + random.nextInt(len - 1);
            for (int j = point; j < len; j++) {
                int t = g1[d][j];
                g1[d][j] = g2[d][j];
                g2[d][j] = t;
            }
        }
    }

    private void mutate(Individual ind, double rate) {
        int[][] g = ind.getGenes();
        for (int d = 0; d < g.length; d++) {
            for (int t = 0; t < g[d].length; t++) {
                if (random.nextDouble() < rate) {
                    g[d][t] = random.nextInt(MoveEncoding.COUNT);
                }
            }
        }
    }

    private List<Individual> environmentalSelection(List<Individual> combined, int n) {
        List<List<Individual>> fronts = fastNonDominatedSort(combined);
        List<Individual> next = new ArrayList<>(n);
        int frontIndex = 0;
        while (frontIndex < fronts.size() && next.size() + fronts.get(frontIndex).size() <= n) {
            crowdingDistanceAssignment(fronts.get(frontIndex));
            next.addAll(fronts.get(frontIndex));
            frontIndex++;
        }
        if (next.size() < n && frontIndex < fronts.size()) {
            List<Individual> last = fronts.get(frontIndex);
            crowdingDistanceAssignment(last);
            last.sort(Comparator.comparingDouble(Individual::getCrowdingDistance).reversed());
            int need = n - next.size();
            for (int i = 0; i < need; i++) {
                next.add(last.get(i));
            }
        }
        assignRankAndCrowding(next);
        return next;
    }

    private void assignRankAndCrowding(List<Individual> population) {
        List<List<Individual>> fronts = fastNonDominatedSort(population);
        for (int i = 0; i < fronts.size(); i++) {
            crowdingDistanceAssignment(fronts.get(i));
            for (Individual ind : fronts.get(i)) {
                ind.setRank(i);
            }
        }
    }

    private List<List<Individual>> fastNonDominatedSort(List<Individual> population) {
        int n = population.size();
        List<List<Integer>> dominates = new ArrayList<>(n);
        int[] dominationCount = new int[n];
        for (int i = 0; i < n; i++) {
            dominates.add(new ArrayList<>());
        }
        for (int p = 0; p < n; p++) {
            for (int q = 0; q < n; q++) {
                if (p == q) {
                    continue;
                }
                Individual ip = population.get(p);
                Individual iq = population.get(q);
                if (dominates(ip, iq)) {
                    dominates.get(p).add(q);
                } else if (dominates(iq, ip)) {
                    dominationCount[p]++;
                }
            }
        }
        List<List<Individual>> fronts = new ArrayList<>();
        List<Integer> currentFront = new ArrayList<>();
        for (int p = 0; p < n; p++) {
            if (dominationCount[p] == 0) {
                currentFront.add(p);
            }
        }
        while (!currentFront.isEmpty()) {
            List<Individual> frontIndividuals = new ArrayList<>();
            for (int p : currentFront) {
                frontIndividuals.add(population.get(p));
            }
            fronts.add(frontIndividuals);

            List<Integer> nextFront = new ArrayList<>();
            for (int p : currentFront) {
                for (int q : dominates.get(p)) {
                    dominationCount[q]--;
                    if (dominationCount[q] == 0) {
                        nextFront.add(q);
                    }
                }
            }
            currentFront = nextFront;
        }
        return fronts;
    }

    private static boolean dominates(Individual p, Individual q) {
        if (p.isFeasible() && !q.isFeasible()) {
            return true;
        }
        if (!p.isFeasible() && q.isFeasible()) {
            return false;
        }
        if (!p.isFeasible() && !q.isFeasible()) {
            return p.getConstraintViolation() < q.getConstraintViolation() - CV_EPS;
        }
        boolean allLeq = true;
        boolean strict = false;
        double[] op = p.getObjectives();
        double[] oq = q.getObjectives();
        for (int k = 0; k < 3; k++) {
            if (op[k] > oq[k]) {
                allLeq = false;
            }
            if (op[k] < oq[k]) {
                strict = true;
            }
        }
        return allLeq && strict;
    }

    private static void crowdingDistanceAssignment(List<Individual> front) {
        int n = front.size();
        if (n == 0) {
            return;
        }
        for (Individual ind : front) {
            ind.setCrowdingDistance(0.0);
        }
        boolean allFeasible = front.stream().allMatch(Individual::isFeasible);
        if (allFeasible) {
            for (int m = 0; m < 3; m++) {
                int obj = m;
                front.sort(Comparator.comparingDouble(i -> i.getObjectives()[obj]));
                front.get(0).setCrowdingDistance(Double.POSITIVE_INFINITY);
                front.get(n - 1).setCrowdingDistance(Double.POSITIVE_INFINITY);
                double fMin = front.get(0).getObjectives()[m];
                double fMax = front.get(n - 1).getObjectives()[m];
                if (Math.abs(fMax - fMin) < 1e-12) {
                    continue;
                }
                for (int i = 1; i < n - 1; i++) {
                    double delta = (front.get(i + 1).getObjectives()[m] - front.get(i - 1).getObjectives()[m])
                            / (fMax - fMin);
                    front.get(i).setCrowdingDistance(front.get(i).getCrowdingDistance() + delta);
                }
            }
        } else {
            front.sort(Comparator.comparingDouble(Individual::getConstraintViolation));
            front.get(0).setCrowdingDistance(Double.POSITIVE_INFINITY);
            front.get(n - 1).setCrowdingDistance(Double.POSITIVE_INFINITY);
            double fMin = front.get(0).getConstraintViolation();
            double fMax = front.get(n - 1).getConstraintViolation();
            if (Math.abs(fMax - fMin) < 1e-12) {
                return;
            }
            for (int i = 1; i < n - 1; i++) {
                double delta = (front.get(i + 1).getConstraintViolation() - front.get(i - 1).getConstraintViolation())
                        / (fMax - fMin);
                front.get(i).setCrowdingDistance(front.get(i).getCrowdingDistance() + delta);
            }
        }
    }
}
