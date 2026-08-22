package org.example.planning.pareto;

import org.example.planning.Individual;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParetoArchiveTest {

    private static Individual individual(double... objectives) {
        Individual individual = new Individual(new int[0][0]);
        System.arraycopy(objectives, 0, individual.getObjectives(), 0, objectives.length);
        return individual;
    }

    @Test
    void rejectsDominatedAndDuplicateCandidatesAndEvictsDominatedMembers() {
        ParetoArchive archive = new ParetoArchive(10);
        assertTrue(archive.offer(individual(1, 1, 1)));
        assertFalse(archive.offer(individual(2, 2, 2)));
        assertFalse(archive.offer(individual(1, 1, 1)));
        assertTrue(archive.offer(individual(0, 0, 0)));
        assertEquals(1, archive.size());
    }

    @Test
    void keepsExtremePointsWhenTrimming() {
        ParetoArchive archive = new ParetoArchive(3);
        List<Individual> candidates = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            candidates.add(individual(i, 10 - i, 0));
        }
        archive.offerAll(candidates);

        assertEquals(3, archive.size());
        assertTrue(archive.members().stream().anyMatch(i -> i.objective(0) == 0));
        assertTrue(archive.members().stream().anyMatch(i -> i.objective(0) == 10));
    }

    @Test
    void nonDominatedSortingSplitsIntoFronts() {
        List<Individual> population = List.of(
                individual(1, 5, 0), individual(5, 1, 0), individual(3, 3, 0),
                individual(6, 6, 0), individual(4, 4, 0));
        List<List<Individual>> fronts = NonDominatedSorting.sortAndAssignRanks(population);

        assertEquals(3, fronts.size());
        assertEquals(3, fronts.get(0).size());
        assertEquals(1, population.get(4).getRank());
        assertEquals(2, population.get(3).getRank());
    }
}
