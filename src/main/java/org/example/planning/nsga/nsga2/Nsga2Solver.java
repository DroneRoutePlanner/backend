package org.example.planning.nsga.nsga2;

import org.example.planning.Individual;
import org.example.planning.MultiDroneRouteEvaluator;
import org.example.planning.PlanningProblem;
import org.example.planning.nsga.CrowdingDistance;
import org.example.planning.nsga.NonDominatedSorting;
import org.example.planning.nsga.NsgaVariation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

public final class Nsga2Solver {

    private final MultiDroneRouteEvaluator routeEvaluator = new MultiDroneRouteEvaluator();

    private final Random randomGenerator;

    public Nsga2Solver(long seed) {
        this.randomGenerator = new Random(seed);
    }

    public List<Individual> run(PlanningProblem problem, int populationSize, int generations) {
        return run(problem, populationSize, generations, null);
    }

    public List<Individual> run(
            PlanningProblem problem,
            int populationSize,
            int generations,
            BiConsumer<Integer, List<Individual>> onGeneration) {
        List<Individual> currentPopulation = new ArrayList<>(populationSize);

        for (int i = 0; i < populationSize; i++) {
            Individual randomIndividual = Individual.randomIndividual(problem, randomGenerator);
            randomIndividual.evaluate(problem, routeEvaluator);
            currentPopulation.add(randomIndividual);
        }

        assignRankAndCrowding(currentPopulation);

        printPopulation(currentPopulation, "Populacja Początkowa");

        for (int generation = 0; generation < generations; generation++) {
            List<Individual> offspringPopulation = makeOffspring(problem, currentPopulation, populationSize);
            List<Individual> combinedPopulation = new ArrayList<>(
                    currentPopulation.size() + offspringPopulation.size());

            combinedPopulation.addAll(currentPopulation);
            combinedPopulation.addAll(offspringPopulation);
            currentPopulation = environmentalSelection(combinedPopulation, populationSize);

            printPopulation(currentPopulation, "Populacja Późniejsza");

            if (onGeneration != null) {
                onGeneration.accept(generation, new ArrayList<>(currentPopulation));
            }
        }

        List<List<Individual>> paretoFronts = NonDominatedSorting.sort(currentPopulation);
        return new ArrayList<>(paretoFronts.get(0));
    }

    private List<Individual> makeOffspring(PlanningProblem problem, List<Individual> currentPopulation,
            int targetSize) {
        List<Individual> offspringPopulation = new ArrayList<>();

        while (offspringPopulation.size() < targetSize) {
            Individual parent1 = binaryTournament(currentPopulation);
            Individual parent2 = binaryTournament(currentPopulation);

            Individual child1 = parent1.copy();
            Individual child2 = parent2.copy();

            if (randomGenerator.nextDouble() < 0.9) {
                NsgaVariation.crossover(randomGenerator, child1, child2);
            }

            NsgaVariation.mutate(randomGenerator, child1, 0.12);
            NsgaVariation.mutate(randomGenerator, child2, 0.12);

            child1.evaluate(problem, routeEvaluator);
            child2.evaluate(problem, routeEvaluator);

            offspringPopulation.add(child1);
            offspringPopulation.add(child2);
        }

        while (offspringPopulation.size() > targetSize) {
            offspringPopulation.remove(offspringPopulation.size() - 1);
        }
        return offspringPopulation;
    }

    private Individual binaryTournament(List<Individual> population) {
        Individual candidateA = population.get(randomGenerator.nextInt(population.size()));
        Individual candidateB = population.get(randomGenerator.nextInt(population.size()));
        if (better(candidateA, candidateB)) {
            return candidateA;
        }
        return candidateB;
    }

    private static boolean better(Individual candidateA, Individual candidateB) {
        if (candidateA.getRank() != candidateB.getRank()) {
            return candidateA.getRank() < candidateB.getRank();
        }
        return candidateA.getCrowdingDistance() > candidateB.getCrowdingDistance();
    }

    private List<Individual> environmentalSelection(List<Individual> combinedPopulation, int targetPopulationSize) {
        List<List<Individual>> sortedFronts = NonDominatedSorting.sort(combinedPopulation);
        List<Individual> selectedPopulation = new ArrayList<>(targetPopulationSize);

        int frontNumber = 0;

        while (frontNumber < sortedFronts.size()
                && selectedPopulation.size() + sortedFronts.get(frontNumber).size() <= targetPopulationSize) {
            CrowdingDistance.assign(sortedFronts.get(frontNumber));
            selectedPopulation.addAll(sortedFronts.get(frontNumber));
            frontNumber++;
        }

        if (selectedPopulation.size() < targetPopulationSize && frontNumber < sortedFronts.size()) {
            List<Individual> splittingFront = sortedFronts.get(frontNumber);
            CrowdingDistance.assign(splittingFront);

            splittingFront.sort(Comparator.comparingDouble(Individual::getCrowdingDistance).reversed());

            int individualsNeededSize = targetPopulationSize - selectedPopulation.size();

            for (int i = 0; i < individualsNeededSize; i++) {
                selectedPopulation.add(splittingFront.get(i));
            }
        }

        assignRankAndCrowding(selectedPopulation);
        return selectedPopulation;
    }

    private void assignRankAndCrowding(List<Individual> population) {
        List<List<Individual>> fronts = NonDominatedSorting.sort(population);

        for (int frontIndex = 0; frontIndex < fronts.size(); frontIndex++) {
            List<Individual> currentFront = fronts.get(frontIndex);
            CrowdingDistance.assign(currentFront);

            for (Individual individual : currentFront) {
                individual.setRank(frontIndex);
            }
        }
    }

    public static void printPopulation(List<Individual> population, String title) {
        System.out.println("\n=========================== " + title.toUpperCase() + " ===========================");
        System.out.printf("%-5s | %-5s | %-13s | %-12s | %-12s | %-12s%n",
                "Nr", "Rank", "Crowding Dist", "Kryterium 0", "Kryterium 1", "Kryterium 2");
        System.out.println("-----------------------------------------------------------------------------");

        for (int i = 0; i < population.size(); i++) {
            Individual ind = population.get(i);
            double[] objectives = ind.getObjectives();

            System.out.printf("#%-4d | %-5d | %-13.4f | %-12.4f | %-12.4f | %-12.4f%n",
                    (i + 1),
                    ind.getRank(),
                    ind.getCrowdingDistance(),
                    objectives[0],
                    objectives[1],
                    objectives[2]);
        }
        System.out.println("=============================================================================\n");
    }
}
