package org.example.planning.nsga.nsga3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceDirectionsTest {

    @Test
    void usesLargestCompleteDasDennisSetNotExceedingPopulation() {
        assertEquals(91, ReferenceDirections.forPopulationSize(100).length);
        assertEquals(21, ReferenceDirections.forPopulationSize(24).length);
        assertEquals(3, ReferenceDirections.forPopulationSize(2).length);
    }

    @Test
    void directionsLieOnUnitSimplex() {
        for (double[] direction : ReferenceDirections.forPopulationSize(50)) {
            double sum = 0;
            for (double v : direction) {
                assertTrue(v >= 0);
                sum += v;
            }
            assertEquals(1.0, sum, 1e-12);
        }
    }
}
