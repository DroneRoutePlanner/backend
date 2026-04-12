package org.example.planning.nsga;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ReferenceDirections {

    private ReferenceDirections() {
    }

    public static int countDasDennis3(int divisions) {
        if (divisions < 1) {
            return 0;
        }
        return (divisions + 2) * (divisions + 1) / 2;
    }

    public static List<double[]> dasDennis3All(int divisions) {
        List<double[]> list = new ArrayList<>();
        if (divisions < 1) {
            return list;
        }
        double p = divisions;
        for (int i = 0; i <= divisions; i++) {
            for (int j = 0; j <= divisions - i; j++) {
                int k = divisions - i - j;
                list.add(new double[] { i / p, j / p, k / p });
            }
        }
        list.sort(Comparator
                .comparingDouble((double[] a) -> a[0])
                .thenComparingDouble(a -> a[1])
                .thenComparingDouble(a -> a[2]));
        return list;
    }

    public static double[][] forPopulationSize(int targetCount) {
        if (targetCount < 1) {
            return new double[0][];
        }
        int div = 1;
        while (countDasDennis3(div) < targetCount) {
            div++;
        }
        List<double[]> all = dasDennis3All(div);
        List<double[]> out = new ArrayList<>(targetCount);
        for (int i = 0; i < targetCount; i++) {
            out.add(all.get(i).clone());
        }
        return out.toArray(new double[0][]);
    }
}
