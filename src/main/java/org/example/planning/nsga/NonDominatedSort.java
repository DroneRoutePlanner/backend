package org.example.planning.nsga;

import java.util.ArrayList;
import java.util.List;

public final class NonDominatedSort {

    private NonDominatedSort() {
    }

    public static List<List<Individual>> sort(List<Individual> population) {
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
                if (Domination.dominates(ip, iq)) {
                    dominates.get(p).add(q);
                } else if (Domination.dominates(iq, ip)) {
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
}
