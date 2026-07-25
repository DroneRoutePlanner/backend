package org.example.planning.gwo;

import org.example.planning.Individual;
import org.example.planning.MoveEncoding;
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

/**
 * Dyskretne MOGWO: archiwum Pareto, elitizm (environmental selection) oraz update
 * segmentowy tras (fragmenty genów od α, β, δ zamiast modyfikacji gen-po-genie).
 */
public final class GwoSolver {

    private static final int COUNT = MoveEncoding.COUNT;

    private final MultiDroneRouteEvaluator evaluator = new MultiDroneRouteEvaluator();
    private final Random random;

    public GwoSolver(long seed) {
        this.random = new Random(seed);
    }

    public List<Individual> run(PlanningProblem problem, int packSize, int iterations) {
        return run(problem, packSize, iterations, null);
    }

    public List<Individual> run(
            PlanningProblem problem,
            int packSize,
            int iterations,
            BiConsumer<Integer, List<Individual>> onIteration) {

        if (packSize < 3) {
            throw new IllegalArgumentException("MOGWO wymaga co najmniej 3 wilków (α, β, δ)");
        }
        if (iterations < 1) {
            throw new IllegalArgumentException("MOGWO wymaga co najmniej 1 iteracji");
        }

        List<Individual> population = new ArrayList<>(packSize);
        for (int i = 0; i < packSize; i++) {
            Individual ind = Individual.randomIndividual(problem, random);
            ind.evaluate(problem, evaluator);
            population.add(ind);
        }

        List<Individual> archive = new ArrayList<>();
        assignRankAndCrowding(population);
        updateArchive(archive, population, packSize);

        for (int iter = 0; iter < iterations; iter++) {
            double a = 2.0 - iter * (2.0 / iterations);

            Individual[] leaders = selectAlphaBetaDelta(archive, population);
            Individual alpha = leaders[0];
            Individual beta = leaders[1];
            Individual delta = leaders[2];

            List<Individual> candidates = new ArrayList<>(packSize);
            for (Individual wolf : population) {
                Individual candidate = discreteSegmentUpdate(wolf, alpha, beta, delta, a);
                candidate.evaluate(problem, evaluator);
                candidates.add(candidate);
            }

            List<Individual> combined = new ArrayList<>(archive.size() + candidates.size());
            for (Individual elite : archive) {
                combined.add(elite.copy());
            }
            combined.addAll(candidates);

            population = environmentalSelection(combined, packSize);
            updateArchive(archive, population, packSize);

            if (iter % 10 == 0 || iter == iterations - 1) {
                printIterationProgress("MOGWO", iter, population, archive.size());
            }

            if (onIteration != null) {
                onIteration.accept(iter, new ArrayList<>(population));
            }
        }

        List<Individual> merged = new ArrayList<>(archive.size() + population.size());
        merged.addAll(archive);
        merged.addAll(population);
        List<List<Individual>> fronts = NonDominatedSorting.sort(merged);
        return new ArrayList<>(fronts.get(0));
    }

    private Individual discreteSegmentUpdate(
            Individual wolf,
            Individual alpha,
            Individual beta,
            Individual delta,
            double a) {

        Individual candidate = wolf.copy();

        double r1 = random.nextDouble();
        double r2 = random.nextDouble();
        double r3 = random.nextDouble();

        double a1 = 2.0 * a * r1 - a;
        double a2 = 2.0 * a * r2 - a;
        double a3 = 2.0 * a * r3 - a;

        if (Math.abs(a1) < 1.0) {
            applyRandomSegmentCopy(candidate, alpha);
        }
        if (Math.abs(a2) < 1.0) {
            applyRandomSegmentCopy(candidate, beta);
        }
        if (Math.abs(a3) < 1.0) {
            applyRandomSegmentCopy(candidate, delta);
        }
        if (Math.abs(a1) >= 1.0 && Math.abs(a2) >= 1.0 && Math.abs(a3) >= 1.0) {
            mutateRandomSegment(candidate);
        }

        NsgaVariation.mutate(random, candidate, Math.min(0.12, a * 0.03));
        return candidate;
    }

    private void applyRandomSegmentCopy(Individual target, Individual leader) {
        int[][] targetGenes = target.getGenes();
        int[][] leaderGenes = leader.getGenes();
        int drones = targetGenes.length;

        int segments = 1 + random.nextInt(Math.min(3, drones));
        for (int s = 0; s < segments; s++) {
            int drone = random.nextInt(drones);
            int len = targetGenes[drone].length;
            if (len < 2) {
                continue;
            }
            int maxSegLen = Math.max(2, len / 8);
            int start = random.nextInt(len - 1);
            int segLen = 1 + random.nextInt(Math.min(len - start, maxSegLen));
            System.arraycopy(leaderGenes[drone], start, targetGenes[drone], start, segLen);
        }
    }

    private void mutateRandomSegment(Individual target) {
        int[][] genes = target.getGenes();
        int drone = random.nextInt(genes.length);
        int len = genes[drone].length;
        if (len == 0) {
            return;
        }
        int start = random.nextInt(len);
        int maxSegLen = Math.max(1, len / 10);
        int segLen = 1 + random.nextInt(Math.min(len - start, maxSegLen));
        for (int t = start; t < start + segLen; t++) {
            genes[drone][t] = random.nextInt(COUNT);
        }
    }

    private Individual[] selectAlphaBetaDelta(List<Individual> archive, List<Individual> population) {
        List<Individual> pool = new ArrayList<>(archive);
        if (pool.size() < 3) {
            List<List<Individual>> fronts = NonDominatedSorting.sort(population);
            for (Individual ind : fronts.get(0)) {
                if (pool.size() >= 3) {
                    break;
                }
                pool.add(ind);
            }
        }
        while (pool.size() < 3) {
            pool.add(pool.get(pool.size() - 1).copy());
        }

        CrowdingDistance.assign(pool);
        pool.sort(Comparator.comparingDouble(Individual::getCrowdingDistance).reversed());
        return new Individual[] { pool.get(0), pool.get(1), pool.get(2) };
    }

    private void updateArchive(List<Individual> archive, List<Individual> candidates, int maxSize) {
        List<Individual> combined = new ArrayList<>(archive.size() + candidates.size());
        for (Individual stored : archive) {
            combined.add(stored.copy());
        }
        for (Individual candidate : candidates) {
            combined.add(candidate.copy());
        }

        List<Individual> nextArchive = new ArrayList<>(maxSize);
        List<List<Individual>> fronts = NonDominatedSorting.sort(combined);
        for (List<Individual> front : fronts) {
            if (nextArchive.size() + front.size() <= maxSize) {
                CrowdingDistance.assign(front);
                for (Individual ind : front) {
                    nextArchive.add(ind.copy());
                }
            } else {
                CrowdingDistance.assign(front);
                front.sort(Comparator.comparingDouble(Individual::getCrowdingDistance).reversed());
                int needed = maxSize - nextArchive.size();
                for (int i = 0; i < needed; i++) {
                    nextArchive.add(front.get(i).copy());
                }
                break;
            }
        }

        archive.clear();
        archive.addAll(nextArchive);
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
            int individualsNeeded = targetPopulationSize - selectedPopulation.size();
            for (int i = 0; i < individualsNeeded; i++) {
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

    public void printIterationProgress(String title, int iteration, List<Individual> population, int archiveSize) {
        if (population == null || population.isEmpty()) {
            System.out.println("--- Iteracja " + iteration + ": Populacja jest pusta ---");
            return;
        }

        String headerText = String.format(" ITERACJA %d [%s] ", iteration, title.toUpperCase());
        System.out.println("\n=======================" + headerText + "=======================");
        System.out.printf("%-5s | %-5s | %-13s | %-12s | %-12s | %-12s%n",
                "Wilk", "Rank", "Crowding Dist", "Kryterium 0", "Kryterium 1", "Kryterium 2");
        System.out.println("-----------------------------------------------------------------------------");

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

        if (population.size() > 3) {
            System.out.println("-----------------------------------------------------------------------------");
            System.out.printf("... oraz %d pozostałych wilków w stadzie.%n", (population.size() - 3));
        }

        long feasibleCount = population.stream().filter(Individual::isFeasible).count();
        System.out.println("-----------------------------------------------------------------------------");
        System.out.printf(
                "Rozwiązania dopuszczalne: %d/%d | Archiwum Pareto: %d%n",
                feasibleCount,
                population.size(),
                archiveSize);
        System.out.println("=============================================================================\n");
    }
}
