package org.example.planning.metrics;

import org.example.planning.Individual;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HypervolumeTest {

    private static final double TOL = 1e-12;

    @Test
    void twoDimensionalUnionOfBoxes() {
        List<double[]> front = List.of(new double[] { 1, 2 }, new double[] { 2, 1 });
        assertEquals(3.0, Hypervolume.of(front, new double[] { 3, 3 }), TOL);
    }

    @Test
    void singlePointInThreeDimensions() {
        assertEquals(0.125, Hypervolume.of(List.of(new double[] { 0.5, 0.5, 0.5 }), new double[] { 1, 1, 1 }), TOL);
        assertEquals(1.0, Hypervolume.of(List.of(new double[] { 0, 0, 0 }), new double[] { 1, 1, 1 }), TOL);
    }

    @Test
    void threeDimensionalFrontMatchesInclusionExclusion() {
        List<double[]> front = List.of(
                new double[] { 0.2, 0.6, 0.7 },
                new double[] { 0.7, 0.2, 0.6 },
                new double[] { 0.6, 0.7, 0.2 });
        // suma objętości − parami przecięcia + przecięcie trójki
        double single = 0.8 * 0.4 * 0.3 + 0.3 * 0.8 * 0.4 + 0.4 * 0.3 * 0.8;
        double pairs = 0.3 * 0.4 * 0.3 + 0.4 * 0.3 * 0.3 + 0.3 * 0.3 * 0.4;
        double triple = 0.3 * 0.3 * 0.3;
        assertEquals(single - pairs + triple, Hypervolume.of(front, new double[] { 1, 1, 1 }), TOL);
    }

    @Test
    void dominatedDuplicateAndOutsidePointsDoNotChangeVolume() {
        List<double[]> base = List.of(new double[] { 0.5, 0.5, 0.5 });
        List<double[]> noisy = List.of(
                new double[] { 0.5, 0.5, 0.5 },
                new double[] { 0.5, 0.5, 0.5 },
                new double[] { 0.9, 0.9, 0.9 },
                new double[] { 0.1, 1.0, 0.1 });
        double[] ref = { 1, 1, 1 };
        assertEquals(Hypervolume.of(base, ref), Hypervolume.of(noisy, ref), TOL);
        assertEquals(0.0, Hypervolume.of(List.of(), ref), TOL);
    }

    @Test
    void normalizedVolumeUsesSharedBoundsAndMargin() {
        Individual a = individual(10, 100, 5);
        Individual b = individual(20, 50, 0);
        ObjectiveBounds bounds = ObjectiveBounds.ofFronts(List.of(List.of(a), List.of(b)));

        // a → (0,1,1), b → (1,0,0); ref = 1.1
        double expected = 1.1 * 0.1 * 0.1 + 0.1 * 1.1 * 1.1 - 0.1 * 0.1 * 0.1;
        double hv = Hypervolume.normalized(List.of(a, b), bounds, 0.1);
        assertEquals(expected, hv, TOL);
        assertEquals(1.1 * 1.1 * 1.1, Hypervolume.maxNormalized(0.1), TOL);
    }

    private static Individual individual(double makespan, double energy, double risk) {
        Individual individual = new Individual(new int[0][0]);
        individual.getObjectives()[0] = makespan;
        individual.getObjectives()[1] = energy;
        individual.getObjectives()[2] = risk;
        return individual;
    }
}
