package org.example.planning.pareto;

import org.example.planning.Individual;

import java.util.ArrayList;
import java.util.List;

/** Szybkie sortowanie niezdominowane (Deb et al., NSGA-II), O(M·N²). */
public final class NonDominatedSorting {

    private NonDominatedSorting() {
    }

    /** Zwraca fronty w kolejności rang (front 0 = rozwiązania niezdominowane). Nie modyfikuje rang. */
    public static List<List<Individual>> sort(List<Individual> population) {
        int n = population.size();
        List<List<Integer>> dominated = new ArrayList<>(n);
        int[] dominationCount = new int[n];
        for (int i = 0; i < n; i++) {
            dominated.add(new ArrayList<>());
        }

        for (int p = 0; p < n; p++) {
            for (int q = p + 1; q < n; q++) {
                Individual ip = population.get(p);
                Individual iq = population.get(q);
                if (Domination.dominates(ip, iq)) {
                    dominated.get(p).add(q);
                    dominationCount[q]++;
                } else if (Domination.dominates(iq, ip)) {
                    dominated.get(q).add(p);
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
            List<Individual> frontIndividuals = new ArrayList<>(currentFront.size());
            for (int p : currentFront) {
                frontIndividuals.add(population.get(p));
            }
            fronts.add(frontIndividuals);

            List<Integer> nextFront = new ArrayList<>();
            for (int p : currentFront) {
                for (int q : dominated.get(p)) {
                    if (--dominationCount[q] == 0) {
                        nextFront.add(q);
                    }
                }
            }
            currentFront = nextFront;
        }
        return fronts;
    }

    /** Sortuje i zapisuje rangę (indeks frontu) w każdym osobniku. */
    public static List<List<Individual>> sortAndAssignRanks(List<Individual> population) {
        List<List<Individual>> fronts = sort(population);
        for (int rank = 0; rank < fronts.size(); rank++) {
            for (Individual individual : fronts.get(rank)) {
                individual.setRank(rank);
            }
        }
        return fronts;
    }

    /** Zbiór niezdominowany populacji (kopia listy; osobniki współdzielone). */
    public static List<Individual> firstFront(List<Individual> population) {
        if (population.isEmpty()) {
            return new ArrayList<>();
        }
        return sort(population).get(0);
    }
}
