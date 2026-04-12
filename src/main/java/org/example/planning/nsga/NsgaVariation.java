package org.example.planning.nsga;

import org.example.planning.MoveEncoding;

import java.util.Random;

public final class NsgaVariation {

    private NsgaVariation() {
    }

    public static void crossover(Random random, Individual c1, Individual c2) {
        int[][] g1 = c1.getGenes();
        int[][] g2 = c2.getGenes();
        int drones = g1.length;
        for (int d = 0; d < drones; d++) {
            if (random.nextBoolean()) {
                continue;
            }
            int len = g1[d].length;
            if (len < 2) {
                continue;
            }
            int point = 1 + random.nextInt(len - 1);
            for (int j = point; j < len; j++) {
                int t = g1[d][j];
                g1[d][j] = g2[d][j];
                g2[d][j] = t;
            }
        }
    }

    public static void mutate(Random random, Individual ind, double rate) {
        int[][] g = ind.getGenes();
        for (int d = 0; d < g.length; d++) {
            for (int t = 0; t < g[d].length; t++) {
                if (random.nextDouble() < rate) {
                    g[d][t] = random.nextInt(MoveEncoding.COUNT);
                }
            }
        }
    }
}
