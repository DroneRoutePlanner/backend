package org.example.planning.pareto;

import org.example.planning.Individual;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Zewnętrzne archiwum rozwiązań niezdominowanych o ograniczonej pojemności (jak w MOGWO).
 * Kandydat zdominowany przez członka archiwum (lub o identycznych kryteriach) jest odrzucany;
 * przyjęty kandydat usuwa z archiwum rozwiązania, które dominuje. Po przekroczeniu pojemności
 * usuwane są rozwiązania z najmniejszą odległością stłoczenia (najgęstsze rejony frontu), dzięki
 * czemu skrajne punkty frontu są zawsze zachowane.
 */
public final class ParetoArchive {

    private final int capacity;

    private final List<Individual> members = new ArrayList<>();

    public ParetoArchive(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Pojemność archiwum musi być dodatnia");
        }
        this.capacity = capacity;
    }

    /** Próbuje dodać kopię kandydata; zwraca {@code true}, jeśli trafił do archiwum. */
    public boolean offer(Individual candidate) {
        for (Individual stored : members) {
            if (Domination.dominates(stored, candidate) || stored.hasSameObjectives(candidate)) {
                return false;
            }
        }
        members.removeIf(stored -> Domination.dominates(candidate, stored));
        members.add(candidate.copy());
        trimToCapacity();
        return true;
    }

    public void offerAll(Collection<Individual> candidates) {
        for (Individual candidate : candidates) {
            offer(candidate);
        }
    }

    private void trimToCapacity() {
        while (members.size() > capacity) {
            CrowdingDistance.assign(members);
            Individual mostCrowded = Collections.min(members,
                    Comparator.comparingDouble(Individual::getCrowdingDistance));
            members.remove(mostCrowded);
        }
    }

    public int size() {
        return members.size();
    }

    /** Widok tylko do odczytu (osobniki współdzielone). */
    public List<Individual> members() {
        return Collections.unmodifiableList(members);
    }

    /** Głębokie kopie członków archiwum. */
    public List<Individual> snapshot() {
        List<Individual> copies = new ArrayList<>(members.size());
        for (Individual member : members) {
            copies.add(member.copy());
        }
        return copies;
    }
}
