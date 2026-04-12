package org.example.planning.nsga;

public final class Domination {

    private static final double CV_EPS = 1e-9;

    private Domination() {
    }

    public static boolean dominates(Individual p, Individual q) {
        if (p.isFeasible() && !q.isFeasible()) {
            return true;
        }
        if (!p.isFeasible() && q.isFeasible()) {
            return false;
        }
        if (!p.isFeasible() && !q.isFeasible()) {
            return p.getConstraintViolation() < q.getConstraintViolation() - CV_EPS;
        }
        boolean allLeq = true;
        boolean strict = false;
        double[] op = p.getObjectives();
        double[] oq = q.getObjectives();
        for (int k = 0; k < Individual.OBJECTIVE_COUNT; k++) {
            if (op[k] > oq[k]) {
                allLeq = false;
            }
            if (op[k] < oq[k]) {
                strict = true;
            }
        }
        return allLeq && strict;
    }
}
