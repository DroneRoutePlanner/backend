package org.example.planning.nsga.nsga3;

import org.example.planning.Individual;

import java.util.List;

/**
 * Adaptacyjna normalizacja NSGA-III (Deb &amp; Jain 2014): przesunięcie o punkt idealny, wyznaczenie
 * punktów ekstremalnych funkcją ASF, hiperpłaszczyzna przez punkty ekstremalne i jej przecięcia z
 * osiami jako skale. Gdy hiperpłaszczyzny nie da się wyznaczyć (układ osobliwy, ujemne lub zerowe
 * przecięcia), stosowany jest punkt nadir zbioru.
 */
final class HyperplaneNormalizer {

    private static final double EPS = 1e-10;

    private static final double ASF_WEIGHT_EPS = 1e-6;

    private final double[] ideal;

    private final double[] intercepts;

    private HyperplaneNormalizer(double[] ideal, double[] intercepts) {
        this.ideal = ideal;
        this.intercepts = intercepts;
    }

    static HyperplaneNormalizer fit(List<Individual> pool) {
        int m = Individual.OBJECTIVE_COUNT;
        double[] ideal = new double[m];
        double[] nadir = new double[m];
        java.util.Arrays.fill(ideal, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(nadir, Double.NEGATIVE_INFINITY);
        for (Individual individual : pool) {
            for (int k = 0; k < m; k++) {
                ideal[k] = Math.min(ideal[k], individual.objective(k));
                nadir[k] = Math.max(nadir[k], individual.objective(k));
            }
        }

        double[] intercepts = hyperplaneIntercepts(pool, ideal);
        if (intercepts == null) {
            intercepts = new double[m];
            for (int k = 0; k < m; k++) {
                intercepts[k] = nadir[k] - ideal[k];
            }
        }
        for (int k = 0; k < m; k++) {
            if (!(intercepts[k] > EPS)) {
                intercepts[k] = 1.0;
            }
        }
        return new HyperplaneNormalizer(ideal, intercepts);
    }

    double[] normalize(Individual individual) {
        double[] z = new double[ideal.length];
        for (int k = 0; k < z.length; k++) {
            z[k] = (individual.objective(k) - ideal[k]) / intercepts[k];
        }
        return z;
    }

    /** Przecięcia hiperpłaszczyzny przez punkty ekstremalne z osiami lub {@code null}, gdy zdegenerowana. */
    private static double[] hyperplaneIntercepts(List<Individual> pool, double[] ideal) {
        int m = ideal.length;
        double[][] extremes = new double[m][];
        for (int axis = 0; axis < m; axis++) {
            double bestAsf = Double.POSITIVE_INFINITY;
            for (Individual individual : pool) {
                double asf = Double.NEGATIVE_INFINITY;
                for (int k = 0; k < m; k++) {
                    double weight = k == axis ? 1.0 : ASF_WEIGHT_EPS;
                    asf = Math.max(asf, (individual.objective(k) - ideal[k]) / weight);
                }
                if (asf < bestAsf) {
                    bestAsf = asf;
                    extremes[axis] = translated(individual, ideal);
                }
            }
        }

        double[] ones = new double[m];
        java.util.Arrays.fill(ones, 1.0);
        double[] planeCoefficients = solve(extremes, ones);
        if (planeCoefficients == null) {
            return null;
        }
        double[] intercepts = new double[m];
        for (int k = 0; k < m; k++) {
            double intercept = 1.0 / planeCoefficients[k];
            if (!Double.isFinite(intercept) || intercept <= EPS) {
                return null;
            }
            intercepts[k] = intercept;
        }
        return intercepts;
    }

    private static double[] translated(Individual individual, double[] ideal) {
        double[] f = new double[ideal.length];
        for (int k = 0; k < f.length; k++) {
            f[k] = individual.objective(k) - ideal[k];
        }
        return f;
    }

    /** Eliminacja Gaussa z częściowym wyborem elementu głównego; {@code null} dla macierzy osobliwej. */
    private static double[] solve(double[][] matrix, double[] rhs) {
        int n = rhs.length;
        double[][] a = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, a[i], 0, n);
            a[i][n] = rhs[i];
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) {
                    pivot = row;
                }
            }
            if (Math.abs(a[pivot][col]) < EPS) {
                return null;
            }
            double[] tmp = a[col];
            a[col] = a[pivot];
            a[pivot] = tmp;
            for (int row = col + 1; row < n; row++) {
                double factor = a[row][col] / a[col][col];
                for (int k = col; k <= n; k++) {
                    a[row][k] -= factor * a[col][k];
                }
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = a[i][n];
            for (int k = i + 1; k < n; k++) {
                sum -= a[i][k] * x[k];
            }
            x[i] = sum / a[i][i];
        }
        return x;
    }
}
