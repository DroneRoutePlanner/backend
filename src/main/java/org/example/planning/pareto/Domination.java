package org.example.planning.pareto;

import org.example.planning.Individual;

/** Relacja dominacji Pareto dla minimalizacji wszystkich kryteriów. */
public final class Domination {

    private Domination() {
    }

    public static boolean dominates(Individual first, Individual second) {
        return dominates(first.getObjectives(), second.getObjectives());
    }

    /** {@code a} dominuje {@code b}: nie gorsze w żadnym kryterium i ściśle lepsze w co najmniej jednym. */
    public static boolean dominates(double[] a, double[] b) {
        boolean strictlyBetterSomewhere = false;
        for (int k = 0; k < a.length; k++) {
            if (a[k] > b[k]) {
                return false;
            }
            if (a[k] < b[k]) {
                strictlyBetterSomewhere = true;
            }
        }
        return strictlyBetterSomewhere;
    }
}
