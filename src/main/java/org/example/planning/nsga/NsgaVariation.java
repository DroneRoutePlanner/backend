package org.example.planning.nsga;

import org.example.planning.Individual;
import org.example.planning.MoveEncoding;

import java.util.Random;

/** Operatory wariacji dla kodowania dyskretnego (kody ruchów): krzyżowanie jednopunktowe per dron i mutacja losowa. */
public final class NsgaVariation {

    private NsgaVariation() {
    }

    /** Krzyżowanie jednopunktowe, niezależnie dla każdego drona (z prawdopodobieństwem 1/2 na drona). */
    public static void crossover(Random random, Individual child1, Individual child2) {
        int[][] genes1 = child1.getGenes();
        int[][] genes2 = child2.getGenes();

        for (int drone = 0; drone < genes1.length; drone++) {
            int length = genes1[drone].length;
            if (length < 2 || random.nextBoolean()) {
                continue;
            }
            int splitPoint = 1 + random.nextInt(length - 1);
            for (int step = splitPoint; step < length; step++) {
                int tmp = genes1[drone][step];
                genes1[drone][step] = genes2[drone][step];
                genes2[drone][step] = tmp;
            }
        }
    }

    /** Każdy gen jest z prawdopodobieństwem {@code rate} zastępowany losowym kodem ruchu. */
    public static void mutate(Random random, Individual individual, double rate) {
        for (int[] droneGenes : individual.getGenes()) {
            for (int step = 0; step < droneGenes.length; step++) {
                if (random.nextDouble() < rate) {
                    droneGenes[step] = random.nextInt(MoveEncoding.COUNT);
                }
            }
        }
    }
}
