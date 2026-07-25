package org.example.planning.nsga;

import java.util.Comparator;
import java.util.List;

import org.example.planning.Individual;

public final class CrowdingDistance {

    private CrowdingDistance() {
    }

    public static void assign(List<Individual> front) {
        int n = front.size();
        if (n == 0) {
            return;
        }
        for (Individual ind : front) {
            ind.setCrowdingDistance(0.0);
        }
        boolean allFeasible = front.stream().allMatch(Individual::isFeasible);
        if (allFeasible) {
            for (int m = 0; m < Individual.OBJECTIVE_COUNT; m++) {
                int obj = m;
                front.sort(Comparator.comparingDouble(i -> i.getObjectives()[obj]));
                front.get(0).setCrowdingDistance(Double.POSITIVE_INFINITY);
                front.get(n - 1).setCrowdingDistance(Double.POSITIVE_INFINITY);
                double fMin = front.get(0).getObjectives()[m];
                double fMax = front.get(n - 1).getObjectives()[m];
                if (Math.abs(fMax - fMin) < 1e-12) {
                    continue;
                }
                for (int i = 1; i < n - 1; i++) {
                    double delta = (front.get(i + 1).getObjectives()[m] - front.get(i - 1).getObjectives()[m])
                            / (fMax - fMin);
                    front.get(i).setCrowdingDistance(front.get(i).getCrowdingDistance() + delta);
                }
            }
        } else {
            front.sort(Comparator.comparingDouble(Individual::getConstraintViolation));
            front.get(0).setCrowdingDistance(Double.POSITIVE_INFINITY);
            front.get(n - 1).setCrowdingDistance(Double.POSITIVE_INFINITY);
            double fMin = front.get(0).getConstraintViolation();
            double fMax = front.get(n - 1).getConstraintViolation();
            if (Math.abs(fMax - fMin) < 1e-12) {
                return;
            }
            for (int i = 1; i < n - 1; i++) {
                double delta = (front.get(i + 1).getConstraintViolation() - front.get(i - 1).getConstraintViolation())
                        / (fMax - fMin);
                front.get(i).setCrowdingDistance(front.get(i).getCrowdingDistance() + delta);
            }
        }
    }
}
