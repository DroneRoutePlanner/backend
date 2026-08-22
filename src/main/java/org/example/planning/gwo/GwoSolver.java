package org.example.planning.gwo;

import org.example.planning.Individual;
import org.example.planning.MoveEncoding;
import org.example.planning.MultiDroneRouteEvaluator;
import org.example.planning.PlanningProblem;
import org.example.planning.pareto.CrowdingDistance;
import org.example.planning.pareto.ParetoArchive;
import org.example.planning.solver.IterationListener;
import org.example.planning.solver.MultiObjectiveSolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Wielokryterialny, dyskretny Grey Wolf Optimizer (MOGWO, Mirjalili et al. 2016) dostosowany do
 * kodowania tras jako ciągów kodów ruchów.
 * <p>
 * Elementy MOGWO: zewnętrzne archiwum rozwiązań niezdominowanych ({@link ParetoArchive}) oraz
 * wybór przywódców α, β, δ z archiwum metodą ruletki preferującą rzadziej zaludnione rejony frontu
 * (tu: wagi proporcjonalne do odległości stłoczenia zamiast siatki hipersześcianów z oryginału).
 * Przywódcy są różnymi rozwiązaniami; gdy archiwum ma mniej niż 3 elementy, uzupełniane jest
 * losowymi wilkami z watahy.
 * <p>
 * Dyskretna aktualizacja pozycji: dla każdego genu każdy przywódca składa propozycję —
 * przy {@code |A| < 1} (eksploatacja) swój własny gen, przy {@code |A| ≥ 1} (eksploracja)
 * gen bieżący wilka lub losowy ruch. Wynik to głosowanie większościowe propozycji z losowym
 * rozstrzyganiem ważonym na korzyść α. Dodatkowo gen mutuje losowo z prawdopodobieństwem
 * malejącym wraz z {@code a} (2 → 0), analogicznie do zaniku składowej eksploracyjnej w GWO.
 * Liczba ocen na iterację jest równa rozmiarowi watahy — taka sama jak w NSGA dla tej samej
 * wielkości populacji, co umożliwia uczciwe porównanie hiperobjętością.
 */
public final class GwoSolver implements MultiObjectiveSolver {

    private static final int LEADER_COUNT = 3;

    /** Prawdopodobieństwo losowej mutacji genu = {@code a * EXPLORATION_MUTATION_SCALE}. */
    private static final double EXPLORATION_MUTATION_SCALE = 0.05;

    /** Wagi rozstrzygania głosowania, gdy wszystkie propozycje są różne: α, β, δ. */
    private static final double[] TIE_BREAK_WEIGHTS = { 0.5, 0.3, 0.2 };

    private static final double WEIGHT_EPS = 1e-9;

    private final MultiDroneRouteEvaluator evaluator = new MultiDroneRouteEvaluator();

    private final Random random;

    public GwoSolver(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public String name() {
        return "MOGWO";
    }

    /**
     * @param packSize   rozmiar watahy i jednocześnie pojemność archiwum
     * @param iterations liczba iteracji
     */
    @Override
    public List<Individual> run(PlanningProblem problem, int packSize, int iterations, IterationListener listener) {
        if (packSize < LEADER_COUNT) {
            throw new IllegalArgumentException("MOGWO wymaga co najmniej 3 wilków (α, β, δ)");
        }
        if (iterations < 1) {
            throw new IllegalArgumentException("MOGWO wymaga co najmniej 1 iteracji");
        }
        Objects.requireNonNull(listener, "listener");

        List<Individual> pack = new ArrayList<>(packSize);
        for (int i = 0; i < packSize; i++) {
            Individual wolf = Individual.randomIndividual(problem, random);
            wolf.evaluate(problem, evaluator);
            pack.add(wolf);
        }

        ParetoArchive archive = new ParetoArchive(packSize);
        archive.offerAll(pack);

        for (int iteration = 0; iteration < iterations; iteration++) {
            double a = 2.0 * (1.0 - (double) iteration / iterations);
            Individual[] leaders = selectLeaders(archive, pack);

            for (int w = 0; w < packSize; w++) {
                Individual moved = new Individual(updatePosition(pack.get(w).getGenes(), leaders, a));
                moved.evaluate(problem, evaluator);
                pack.set(w, moved);
            }

            archive.offerAll(pack);
            listener.onIteration(iteration, archive.members());
        }
        return archive.snapshot();
    }

    private int[][] updatePosition(int[][] current, Individual[] leaders, double a) {
        int[][] next = new int[current.length][];
        int[] proposals = new int[LEADER_COUNT];

        for (int d = 0; d < current.length; d++) {
            next[d] = new int[current[d].length];
            for (int t = 0; t < current[d].length; t++) {
                for (int k = 0; k < LEADER_COUNT; k++) {
                    double coefficientA = 2.0 * a * random.nextDouble() - a;
                    if (Math.abs(coefficientA) < 1.0) {
                        proposals[k] = leaders[k].getGenes()[d][t];
                    } else {
                        proposals[k] = random.nextBoolean() ? current[d][t] : random.nextInt(MoveEncoding.COUNT);
                    }
                }
                int gene = vote(proposals);
                if (random.nextDouble() < a * EXPLORATION_MUTATION_SCALE) {
                    gene = random.nextInt(MoveEncoding.COUNT);
                }
                next[d][t] = gene;
            }
        }
        return next;
    }

    private int vote(int[] proposals) {
        if (proposals[0] == proposals[1] || proposals[0] == proposals[2]) {
            return proposals[0];
        }
        if (proposals[1] == proposals[2]) {
            return proposals[1];
        }
        double r = random.nextDouble();
        if (r < TIE_BREAK_WEIGHTS[0]) {
            return proposals[0];
        }
        return r < TIE_BREAK_WEIGHTS[0] + TIE_BREAK_WEIGHTS[1] ? proposals[1] : proposals[2];
    }

    /** Ruletka po odległości stłoczenia (bez zwracania) — trzech różnych przywódców. */
    private Individual[] selectLeaders(ParetoArchive archive, List<Individual> pack) {
        List<Individual> pool = new ArrayList<>(archive.members());
        while (pool.size() < LEADER_COUNT) {
            pool.add(pack.get(random.nextInt(pack.size())));
        }
        CrowdingDistance.assign(pool);
        double[] weights = rouletteWeights(pool);

        Individual[] leaders = new Individual[LEADER_COUNT];
        boolean[] taken = new boolean[pool.size()];
        for (int k = 0; k < LEADER_COUNT; k++) {
            int index = spin(weights, taken);
            leaders[k] = pool.get(index);
            taken[index] = true;
        }
        return leaders;
    }

    /** Skrajne punkty frontu (+∞) dostają dwukrotność największej skończonej odległości. */
    private static double[] rouletteWeights(List<Individual> pool) {
        double maxFinite = 0.0;
        for (Individual individual : pool) {
            double cd = individual.getCrowdingDistance();
            if (Double.isFinite(cd)) {
                maxFinite = Math.max(maxFinite, cd);
            }
        }
        double boundaryWeight = maxFinite > 0.0 ? 2.0 * maxFinite : 1.0;

        double[] weights = new double[pool.size()];
        for (int i = 0; i < weights.length; i++) {
            double cd = pool.get(i).getCrowdingDistance();
            weights[i] = (Double.isFinite(cd) ? cd : boundaryWeight) + WEIGHT_EPS;
        }
        return weights;
    }

    private int spin(double[] weights, boolean[] taken) {
        double total = 0.0;
        int lastFree = -1;
        for (int i = 0; i < weights.length; i++) {
            if (!taken[i]) {
                total += weights[i];
                lastFree = i;
            }
        }
        double target = random.nextDouble() * total;
        double accumulated = 0.0;
        for (int i = 0; i < weights.length; i++) {
            if (taken[i]) {
                continue;
            }
            accumulated += weights[i];
            if (accumulated >= target) {
                return i;
            }
        }
        return lastFree;
    }
}
