package org.example.planning.nsga;

import org.example.planning.Individual;

public final class Domination {

    private Domination() {
    }

    public static boolean dominates(Individual firstIndividual, Individual secondIndividual) {
        boolean notWorse = true;
        boolean isStrictlyBetter = false;
        double[] firstObjectives = firstIndividual.getObjectives();
        double[] secondObjectives = secondIndividual.getObjectives();

        for (int k = 0; k < Individual.OBJECTIVE_COUNT; k++) {
            if (firstObjectives[k] > secondObjectives[k]) {
                notWorse = false;
            }
            if (firstObjectives[k] < secondObjectives[k]) {
                isStrictlyBetter = true;
            }
        }
        return notWorse && isStrictlyBetter;
    }
}
