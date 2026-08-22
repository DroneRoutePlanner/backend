package org.example.planning;

/**
 * Wynik oceny trasy zespołu dronów — trzy minimalizowane kryteria.
 *
 * @param makespan       czas zakończenia misji przez ostatniego drona (w tickach symulacji)
 * @param totalEnergy    łączna energia zużyta przez wszystkie drony
 * @param totalRadarRisk łączne ryzyko wykrycia przez radary
 */
public record RouteEvaluationResult(double makespan, double totalEnergy, double totalRadarRisk) {

    /** Kryteria w kolejności indeksów z {@link Individual}: makespan, energia, ryzyko radarowe. */
    public double[] objectives() {
        return new double[] { makespan, totalEnergy, totalRadarRisk };
    }
}
