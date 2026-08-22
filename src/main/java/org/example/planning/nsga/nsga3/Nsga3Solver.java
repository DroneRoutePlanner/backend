package org.example.planning.nsga.nsga3;

import org.example.planning.Individual;
import org.example.planning.nsga.AbstractNsgaSolver;
import org.example.planning.pareto.NonDominatedSorting;

import java.util.ArrayList;
import java.util.List;

/**
 * NSGA-III (Deb &amp; Jain 2014): sortowanie niezdominowane, adaptacyjna normalizacja
 * ({@link HyperplaneNormalizer}), skojarzenie z punktami referencyjnymi Das–Dennis i dobór
 * niszowy z ostatniego frontu. Selekcja rodziców: turniej po randze z losowym rozstrzyganiem
 * remisów (NSGA-III nie używa odległości stłoczenia).
 */
public final class Nsga3Solver extends AbstractNsgaSolver {

    private static final double EPS = 1e-12;

    private double[][] referenceDirections = new double[0][];

    private int referenceDirectionsForSize = -1;

    public Nsga3Solver(long seed) {
        super(seed);
    }

    @Override
    public String name() {
        return "NSGA-III";
    }

    @Override
    protected Individual selectParent(List<Individual> population) {
        Individual a = population.get(random.nextInt(population.size()));
        Individual b = population.get(random.nextInt(population.size()));
        if (a.getRank() != b.getRank()) {
            return a.getRank() < b.getRank() ? a : b;
        }
        return random.nextBoolean() ? a : b;
    }

    @Override
    protected List<Individual> environmentalSelection(List<Individual> combined, int targetSize) {
        double[][] refDirs = referenceDirectionsFor(targetSize);
        List<List<Individual>> fronts = NonDominatedSorting.sortAndAssignRanks(combined);
        List<Individual> next = new ArrayList<>(targetSize);

        int frontIndex = 0;
        while (frontIndex < fronts.size() && next.size() + fronts.get(frontIndex).size() <= targetSize) {
            next.addAll(fronts.get(frontIndex));
            frontIndex++;
        }
        if (next.size() < targetSize && frontIndex < fronts.size()) {
            nichingFill(next, fronts.get(frontIndex), targetSize - next.size(), refDirs);
        }
        return next;
    }

    private double[][] referenceDirectionsFor(int populationSize) {
        if (referenceDirectionsForSize != populationSize) {
            referenceDirections = ReferenceDirections.forPopulationSize(populationSize);
            referenceDirectionsForSize = populationSize;
        }
        return referenceDirections;
    }

    /** Osobnik skojarzony z najbliższym punktem referencyjnym (odległość prostopadła). */
    private record Association(Individual individual, int niche, double distance) {
    }

    /** Dobór niszowy (Deb &amp; Jain, Algorithm 4): preferuj najmniej zapełnione nisze. */
    private void nichingFill(List<Individual> next, List<Individual> lastFront, int need, double[][] refDirs) {
        List<Individual> pool = new ArrayList<>(next.size() + lastFront.size());
        pool.addAll(next);
        pool.addAll(lastFront);
        HyperplaneNormalizer normalizer = HyperplaneNormalizer.fit(pool);

        int[] nicheCount = new int[refDirs.length];
        for (Individual selected : next) {
            nicheCount[associate(selected, normalizer, refDirs).niche()]++;
        }

        List<Association> candidates = new ArrayList<>(lastFront.size());
        for (Individual candidate : lastFront) {
            candidates.add(associate(candidate, normalizer, refDirs));
        }

        boolean[] excluded = new boolean[refDirs.length];
        int picked = 0;
        while (picked < need && !candidates.isEmpty()) {
            int niche = leastCrowdedNiche(nicheCount, excluded);
            if (niche < 0) {
                break;
            }
            List<Association> inNiche = new ArrayList<>();
            for (Association candidate : candidates) {
                if (candidate.niche() == niche) {
                    inNiche.add(candidate);
                }
            }
            if (inNiche.isEmpty()) {
                excluded[niche] = true;
                continue;
            }
            Association chosen = nicheCount[niche] == 0 ? closest(inNiche) : inNiche.get(random.nextInt(inNiche.size()));
            next.add(chosen.individual());
            candidates.remove(chosen);
            nicheCount[niche]++;
            picked++;
        }
    }

    private static Association closest(List<Association> associations) {
        Association best = associations.get(0);
        for (Association candidate : associations) {
            if (candidate.distance() < best.distance()) {
                best = candidate;
            }
        }
        return best;
    }

    private static int leastCrowdedNiche(int[] nicheCount, boolean[] excluded) {
        int best = -1;
        for (int j = 0; j < nicheCount.length; j++) {
            if (!excluded[j] && (best < 0 || nicheCount[j] < nicheCount[best])) {
                best = j;
            }
        }
        return best;
    }

    private static Association associate(Individual individual, HyperplaneNormalizer normalizer, double[][] refDirs) {
        double[] normalized = normalizer.normalize(individual);
        int bestNiche = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int j = 0; j < refDirs.length; j++) {
            double distance = perpendicularDistance(normalized, refDirs[j]);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestNiche = j;
            }
        }
        return new Association(individual, bestNiche, bestDistance);
    }

    private static double perpendicularDistance(double[] point, double[] direction) {
        double dirNormSq = 0.0;
        double dot = 0.0;
        for (int k = 0; k < point.length; k++) {
            dirNormSq += direction[k] * direction[k];
            dot += point[k] * direction[k];
        }
        if (dirNormSq < EPS) {
            return Double.POSITIVE_INFINITY;
        }
        double t = dot / dirNormSq;
        double distSq = 0.0;
        for (int k = 0; k < point.length; k++) {
            double diff = point[k] - t * direction[k];
            distSq += diff * diff;
        }
        return Math.sqrt(distSq);
    }
}
