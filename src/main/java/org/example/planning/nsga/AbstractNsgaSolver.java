package org.example.planning.nsga;

import org.example.planning.Individual;
import org.example.planning.MultiDroneRouteEvaluator;
import org.example.planning.PlanningProblem;
import org.example.planning.pareto.NonDominatedSorting;
import org.example.planning.solver.IterationListener;
import org.example.planning.solver.MultiObjectiveSolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Wspólny szkielet NSGA-II / NSGA-III: populacja początkowa → (selekcja rodziców → krzyżowanie →
 * mutacja → ocena → selekcja środowiskowa (μ+λ)) × pokolenia. Klasy pochodne definiują selekcję
 * rodziców i selekcję środowiskową (która musi nadać rangi zwracanej populacji).
 */
public abstract class AbstractNsgaSolver implements MultiObjectiveSolver {

    public static final double CROSSOVER_PROBABILITY = 0.9;

    public static final double MUTATION_RATE = 0.12;

    protected final Random random;

    private final MultiDroneRouteEvaluator evaluator = new MultiDroneRouteEvaluator();

    protected AbstractNsgaSolver(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public final List<Individual> run(
            PlanningProblem problem, int populationSize, int generations, IterationListener listener) {
        if (populationSize < 2) {
            throw new IllegalArgumentException(name() + " wymaga populacji co najmniej 2 osobników");
        }
        if (generations < 1) {
            throw new IllegalArgumentException(name() + " wymaga co najmniej 1 pokolenia");
        }
        Objects.requireNonNull(listener, "listener");

        List<Individual> population = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            Individual individual = Individual.randomIndividual(problem, random);
            individual.evaluate(problem, evaluator);
            population.add(individual);
        }
        population = environmentalSelection(population, populationSize);

        for (int generation = 0; generation < generations; generation++) {
            List<Individual> offspring = makeOffspring(problem, population, populationSize);
            List<Individual> combined = new ArrayList<>(population.size() + offspring.size());
            combined.addAll(population);
            combined.addAll(offspring);

            population = environmentalSelection(combined, populationSize);
            listener.onIteration(generation, Collections.unmodifiableList(rankZero(population)));
        }

        List<Individual> front = new ArrayList<>();
        for (Individual individual : NonDominatedSorting.firstFront(population)) {
            front.add(individual.copy());
        }
        return front;
    }

    /** Wybiera {@code targetSize} osobników z populacji połączonej; nadaje rangi wynikowi. */
    protected abstract List<Individual> environmentalSelection(List<Individual> combined, int targetSize);

    protected abstract Individual selectParent(List<Individual> population);

    private List<Individual> makeOffspring(PlanningProblem problem, List<Individual> population, int count) {
        List<Individual> offspring = new ArrayList<>(count);
        while (offspring.size() < count) {
            Individual child1 = selectParent(population).copy();
            Individual child2 = selectParent(population).copy();

            if (random.nextDouble() < CROSSOVER_PROBABILITY) {
                NsgaVariation.crossover(random, child1, child2);
            }
            NsgaVariation.mutate(random, child1, MUTATION_RATE);
            NsgaVariation.mutate(random, child2, MUTATION_RATE);

            child1.evaluate(problem, evaluator);
            offspring.add(child1);
            if (offspring.size() < count) {
                child2.evaluate(problem, evaluator);
                offspring.add(child2);
            }
        }
        return offspring;
    }

    private static List<Individual> rankZero(List<Individual> population) {
        List<Individual> front = new ArrayList<>();
        for (Individual individual : population) {
            if (individual.getRank() == 0) {
                front.add(individual);
            }
        }
        return front;
    }
}
