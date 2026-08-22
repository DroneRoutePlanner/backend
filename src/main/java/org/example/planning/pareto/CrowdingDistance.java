package org.example.planning.pareto;

import org.example.planning.Individual;

import java.util.Comparator;
import java.util.List;

/** Odległość stłoczenia (NSGA-II). */
public final class CrowdingDistance {

    private static final double EPS = 1e-12;

    private CrowdingDistance() {
    }

    /**
     * Przypisuje odległość stłoczenia osobnikom jednego frontu. Skrajne osobniki w każdym
     * kryterium otrzymują {@code +∞}. Lista jest sortowana w miejscu (kolejność ulega zmianie).
     */
    public static void assign(List<Individual> front) {
        int n = front.size();
        if (n == 0) {
            return;
        }
        for (Individual individual : front) {
            individual.setCrowdingDistance(0.0);
        }
        for (int m = 0; m < Individual.OBJECTIVE_COUNT; m++) {
            final int objective = m;
            front.sort(Comparator.comparingDouble(i -> i.objective(objective)));
            front.get(0).setCrowdingDistance(Double.POSITIVE_INFINITY);
            front.get(n - 1).setCrowdingDistance(Double.POSITIVE_INFINITY);

            double range = front.get(n - 1).objective(m) - front.get(0).objective(m);
            if (range < EPS) {
                continue;
            }
            for (int i = 1; i < n - 1; i++) {
                double gap = (front.get(i + 1).objective(m) - front.get(i - 1).objective(m)) / range;
                Individual current = front.get(i);
                current.setCrowdingDistance(current.getCrowdingDistance() + gap);
            }
        }
    }
}
