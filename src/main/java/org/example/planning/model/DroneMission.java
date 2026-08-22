package org.example.planning.model;

import org.example.environment.Vector3d;

/**
 * Misja pojedynczego drona: start, cel i budżet energii.
 * <p>
 * Uwaga: {@code energyBudget} jest obecnie wyłącznie parametrem opisowym — ewaluator nie egzekwuje
 * zasięgu (energia jest minimalizowana jako kryterium, a nie ograniczana).
 */
public record DroneMission(
        int id,
        Vector3d start,
        Vector3d goal,
        double energyBudget
) {
}
