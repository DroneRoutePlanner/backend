package org.example.planning.metrics;

import org.example.planning.Individual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Dokładna hiperobjętość (Zitzler &amp; Thiele) dla minimalizacji: objętość obszaru zdominowanego
 * przez front i ograniczonego punktem referencyjnym. Algorytm rekurencyjnego cięcia wymiarów
 * (O(n^{m-1} log n)) — w zupełności wystarczający dla 3 kryteriów i frontów rzędu setek punktów.
 * Punkty nieleżące ściśle poniżej punktu referencyjnego we wszystkich kryteriach są pomijane.
 */
public final class Hypervolume {

    private Hypervolume() {
    }

    public static double of(Collection<double[]> points, double[] referencePoint) {
        int m = referencePoint.length;
        List<double[]> inside = new ArrayList<>();
        for (double[] point : points) {
            if (point.length != m) {
                throw new IllegalArgumentException("Punkt ma " + point.length + " kryteriów, oczekiwano " + m);
            }
            if (strictlyBelow(point, referencePoint)) {
                inside.add(point);
            }
        }
        return volume(ParetoSet.nonDominated(inside), referencePoint, m);
    }

    public static double ofIndividuals(Collection<Individual> front, double[] referencePoint) {
        List<double[]> points = new ArrayList<>(front.size());
        for (Individual individual : front) {
            points.add(individual.getObjectives());
        }
        return of(points, referencePoint);
    }

    /**
     * Hiperobjętość frontu po normalizacji do wspólnych granic (ideal → 0, nadir → 1) z punktem
     * referencyjnym {@code 1 + margin} w każdym kryterium. Wartość maksymalna (cały sześcian) to
     * {@link #maxNormalized(double)}; dla porównań podaje się zwykle iloraz obu wartości.
     */
    public static double normalized(Collection<Individual> front, ObjectiveBounds bounds, double margin) {
        double[] reference = new double[Individual.OBJECTIVE_COUNT];
        Arrays.fill(reference, 1.0 + margin);
        return of(bounds.normalizeAll(front), reference);
    }

    public static double maxNormalized(double margin) {
        return Math.pow(1.0 + margin, Individual.OBJECTIVE_COUNT);
    }

    private static boolean strictlyBelow(double[] point, double[] reference) {
        for (int k = 0; k < point.length; k++) {
            if (!(point[k] < reference[k])) {
                return false;
            }
        }
        return true;
    }

    /** Objętość sumy prostopadłościanów [p, ref] dla punktów niezdominowanych w wymiarze {@code dim}. */
    private static double volume(List<double[]> points, double[] reference, int dim) {
        if (points.isEmpty()) {
            return 0.0;
        }
        if (dim == 1) {
            double min = Double.POSITIVE_INFINITY;
            for (double[] point : points) {
                min = Math.min(min, point[0]);
            }
            return reference[0] - min;
        }

        int last = dim - 1;
        List<double[]> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble(p -> p[last]));

        double total = 0.0;
        List<double[]> projected = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            double[] point = sorted.get(i);
            projected.add(Arrays.copyOf(point, last));
            double sliceTop = i + 1 < sorted.size() ? sorted.get(i + 1)[last] : reference[last];
            double thickness = sliceTop - point[last];
            if (thickness > 0.0) {
                total += thickness * volume(ParetoSet.nonDominated(projected), reference, last);
            }
        }
        return total;
    }
}
