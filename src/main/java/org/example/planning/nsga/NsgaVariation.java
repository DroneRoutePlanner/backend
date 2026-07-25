package org.example.planning.nsga;

import org.example.planning.Individual;
import org.example.planning.MoveEncoding;

import java.util.Random;

public final class NsgaVariation {

    private NsgaVariation() {
    }

    // krzyżowanie jednopunktowe a nie SBX
    public static void crossover(Random random, Individual child1, Individual child2) {
        int[][] genes1 = child1.getGenes();
        int[][] genes2 = child2.getGenes();
        int drones = genes1.length;

        for (int drone = 0; drone < drones; drone++) {
            if (random.nextBoolean()) {
                continue;
            }

            int len = genes1[drone].length;
            if (len < 2) {
                continue;
            }

            int splitPoint = 1 + random.nextInt(len - 1);
            for (int j = splitPoint; j < len; j++) {
                int t = genes1[drone][j];
                genes1[drone][j] = genes2[drone][j];
                genes2[drone][j] = t;
            }
        }
    }

    public static void mutate(Random random, Individual ind, double rate) {
        int[][] droneGenes = ind.getGenes();
        for (int droneIndex = 0; droneIndex < droneGenes.length; droneIndex++) {
            for (int stepIndex = 0; stepIndex < droneGenes[droneIndex].length; stepIndex++) {
                if (random.nextDouble() < rate) {
                    droneGenes[droneIndex][stepIndex] = random.nextInt(MoveEncoding.COUNT);
                }
            }
        }
    }
}
