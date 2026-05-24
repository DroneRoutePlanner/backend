package org.example.planning.nsga.nsga2;

import org.example.planning.MultiDroneRouteEvaluator;
import org.example.planning.PlanningProblem;
import org.example.planning.nsga.CrowdingDistance;
import org.example.planning.nsga.Individual;
import org.example.planning.nsga.NonDominatedSorting;
import org.example.planning.nsga.NsgaVariation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

public final class Nsga2Solver {

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
            BiConsumer<Integer, List<Individual>> onGeneration) {
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

        List<List<Individual>> fronts = NonDominatedSorting.sort(population);
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

    private List<Individual> environmentalSelection(List<Individual> combined, int n) {
        List<List<Individual>> fronts = NonDominatedSorting.sort(combined);
        List<Individual> next = new ArrayList<>(n);
        int frontIndex = 0;
        while (frontIndex < fronts.size() && next.size() + fronts.get(frontIndex).size() <= n) {
            CrowdingDistance.assign(fronts.get(frontIndex));
            next.addAll(fronts.get(frontIndex));
            frontIndex++;
        }
        if (next.size() < n && frontIndex < fronts.size()) {
            List<Individual> last = fronts.get(frontIndex);
            CrowdingDistance.assign(last);
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
        List<List<Individual>> fronts = NonDominatedSorting.sort(population);
        for (int i = 0; i < fronts.size(); i++) {
            CrowdingDistance.assign(fronts.get(i));
            for (Individual ind : fronts.get(i)) {
                ind.setRank(i);
            }
        }
    }
}
