package org.example.planning.nsga.nsga3;

import org.example.planning.Individual;

import java.util.ArrayList;
import java.util.List;

/** Punkty referencyjne Das–Dennis na sympleksie jednostkowym (dla trzech kryteriów). */
public final class ReferenceDirections {

    private ReferenceDirections() {
    }

    /** Liczba punktów Das–Dennis dla {@code divisions} podziałów i 3 kryteriów: C(p+2, 2). */
    public static int count(int divisions) {
        return divisions < 1 ? 0 : (divisions + 2) * (divisions + 1) / 2;
    }

    public static double[][] dasDennis(int divisions) {
        if (divisions < 1) {
            throw new IllegalArgumentException("Liczba podziałów musi być ≥ 1");
        }
        List<double[]> points = new ArrayList<>(count(divisions));
        double p = divisions;
        for (int i = 0; i <= divisions; i++) {
            for (int j = 0; j <= divisions - i; j++) {
                int k = divisions - i - j;
                points.add(new double[] { i / p, j / p, k / p });
            }
        }
        return points.toArray(new double[0][]);
    }

    /**
     * Pełny (nieobcięty) zbiór Das–Dennis o największej liczbie podziałów, dla której liczba punktów
     * nie przekracza {@code populationSize} (co najmniej 3 punkty). Pełny zbiór gwarantuje równomierne
     * pokrycie sympleksu — obcięcie posortowanej listy faworyzowałoby jeden rejon frontu.
     */
    public static double[][] forPopulationSize(int populationSize) {
        if (Individual.OBJECTIVE_COUNT != 3) {
            throw new IllegalStateException("Generator Das–Dennis zaimplementowano dla 3 kryteriów");
        }
        int divisions = 1;
        while (count(divisions + 1) <= populationSize) {
            divisions++;
        }
        return dasDennis(divisions);
    }
}
