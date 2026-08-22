package org.example.planning.nsga.nsga2;

import org.example.planning.Individual;
import org.example.planning.nsga.AbstractNsgaSolver;
import org.example.planning.pareto.CrowdingDistance;
import org.example.planning.pareto.NonDominatedSorting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * NSGA-II (Deb et al. 2002): sortowanie niezdominowane + odległość stłoczenia,
 * turniej binarny po (ranga, stłoczenie).
 */
public final class Nsga2Solver extends AbstractNsgaSolver {

    private static final Comparator<Individual> MOST_CROWDING_FIRST =
            Comparator.comparingDouble(Individual::getCrowdingDistance).reversed();

    public Nsga2Solver(long seed) {
        super(seed);
    }

    @Override
    public String name() {
        return "NSGA-II";
    }

    @Override
    protected Individual selectParent(List<Individual> population) {
        Individual a = population.get(random.nextInt(population.size()));
        Individual b = population.get(random.nextInt(population.size()));
        return crowdedComparison(a, b) ? a : b;
    }

    /** Operator porównania stłoczonego: mniejsza ranga, a przy równej większa odległość stłoczenia. */
    private static boolean crowdedComparison(Individual a, Individual b) {
        if (a.getRank() != b.getRank()) {
            return a.getRank() < b.getRank();
        }
        return a.getCrowdingDistance() > b.getCrowdingDistance();
    }

    @Override
    protected List<Individual> environmentalSelection(List<Individual> combined, int targetSize) {
        List<List<Individual>> fronts = NonDominatedSorting.sortAndAssignRanks(combined);
        List<Individual> next = new ArrayList<>(targetSize);

        int frontIndex = 0;
        while (frontIndex < fronts.size() && next.size() + fronts.get(frontIndex).size() <= targetSize) {
            List<Individual> front = fronts.get(frontIndex);
            CrowdingDistance.assign(front);
            next.addAll(front);
            frontIndex++;
        }

        if (next.size() < targetSize && frontIndex < fronts.size()) {
            List<Individual> splittingFront = fronts.get(frontIndex);
            CrowdingDistance.assign(splittingFront);
            splittingFront.sort(MOST_CROWDING_FIRST);
            next.addAll(splittingFront.subList(0, targetSize - next.size()));
        }
        return next;
    }
}
