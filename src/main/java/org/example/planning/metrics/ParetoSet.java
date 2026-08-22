package org.example.planning.metrics;

import org.example.planning.pareto.Domination;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Operacje na zbiorach punktów w przestrzeni kryteriów (minimalizacja). */
public final class ParetoSet {

    private ParetoSet() {
    }

    /** Punkty niezdominowane, bez duplikatów; kolejność pierwszego wystąpienia zachowana. */
    public static List<double[]> nonDominated(List<double[]> points) {
        List<double[]> result = new ArrayList<>();
        outer:
        for (int i = 0; i < points.size(); i++) {
            double[] candidate = points.get(i);
            for (int j = 0; j < points.size(); j++) {
                if (i == j) {
                    continue;
                }
                double[] other = points.get(j);
                if (Domination.dominates(other, candidate) || (j < i && Arrays.equals(other, candidate))) {
                    continue outer;
                }
            }
            result.add(candidate);
        }
        return result;
    }
}
