package org.example.planning.metrics;

import org.example.planning.Individual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Punkt idealny i nadir zbioru punktów — wspólny układ odniesienia do normalizacji kryteriów do
 * [0,1] przed porównaniem frontów różnych algorytmów (np. hiperobjętością).
 */
public record ObjectiveBounds(double[] ideal, double[] nadir) {

    private static final double EPS = 1e-12;

    /** Granice wyznaczone po sumie wszystkich frontów. */
    public static ObjectiveBounds ofFronts(Collection<? extends Collection<Individual>> fronts) {
        List<double[]> points = new ArrayList<>();
        for (Collection<Individual> front : fronts) {
            for (Individual individual : front) {
                points.add(individual.getObjectives());
            }
        }
        return ofPoints(points);
    }

    public static ObjectiveBounds ofPoints(Collection<double[]> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Brak punktów do wyznaczenia granic");
        }
        int m = points.iterator().next().length;
        double[] ideal = new double[m];
        double[] nadir = new double[m];
        Arrays.fill(ideal, Double.POSITIVE_INFINITY);
        Arrays.fill(nadir, Double.NEGATIVE_INFINITY);
        for (double[] point : points) {
            for (int k = 0; k < m; k++) {
                ideal[k] = Math.min(ideal[k], point[k]);
                nadir[k] = Math.max(nadir[k], point[k]);
            }
        }
        return new ObjectiveBounds(ideal, nadir);
    }

    /** (f − ideal) / (nadir − ideal); kryterium o zerowym zakresie daje 0. */
    public double[] normalize(double[] objectives) {
        double[] z = new double[objectives.length];
        for (int k = 0; k < z.length; k++) {
            double range = nadir[k] - ideal[k];
            z[k] = range < EPS ? 0.0 : (objectives[k] - ideal[k]) / range;
        }
        return z;
    }

    public List<double[]> normalizeAll(Collection<Individual> front) {
        List<double[]> normalized = new ArrayList<>(front.size());
        for (Individual individual : front) {
            normalized.add(normalize(individual.getObjectives()));
        }
        return normalized;
    }
}
