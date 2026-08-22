# DroneRoutePlanner — backend

Symulator i algorytmy metaheurystyczne (NSGA-II, NSGA-III, MOGWO) do planowania tras zespołu
dronów w niestacjonarnym środowisku (teren, strefy zakazu lotu, radary, zmienny wiatr).

## Model problemu

* **Kodowanie**: trasa każdego drona to ciąg `maxStepsPerDrone` kodów ruchów (26 kierunków siatki 3D,
  `MoveEncoding`). Osobnik = `int[dron][krok]`.
* **Symulacja** (`MultiDroneRouteEvaluator`): drony ruszają się naprzemiennie (round-robin), w ticku `t`
  porusza się dron `t mod n`. Ruch naruszający ograniczenie (granice mapy, pułap, teren, strefa zakazu
  lotu, komórka zajęta przez inny dron) jest **odrzucany** — dron zostaje w miejscu, gen jest zużyty.
  Dzięki temu kryteria nie zawierają kar, a każda trasa jest dopuszczalna przestrzennie.
* **Kryteria (minimalizowane)**: `makespan` (tick dotarcia ostatniego drona; nieosiągnięty cel =
  horyzont + pozostała odległość Czebyszewa), `totalEnergy` (ruch poziomy/pionowy/wznoszenie + koszt
  lotu pod wiatr), `totalRadarRisk` (suma `risk·10 + 150` za każdy krok w zasięgu radaru).
* **Nieegzekwowane** (świadomie): budżet energii misji (`DroneMission.energyBudget`) — energia jest
  kryterium, nie ograniczeniem.

## Algorytmy (`MultiObjectiveSolver`)

| Klasa | Algorytm | Uwagi implementacyjne |
|---|---|---|
| `nsga.nsga2.Nsga2Solver` | NSGA-II | sortowanie niezdominowane, crowding distance, turniej (ranga, stłoczenie) |
| `nsga.nsga3.Nsga3Solver` | NSGA-III | pełny zbiór Das–Dennis (największy ≤ N), normalizacja hiperpłaszczyzną (ASF + przecięcia), dobór niszowy Deb & Jain |
| `gwo.GwoSolver` | MOGWO | archiwum Pareto z redukcją przez crowding, przywódcy α/β/δ ruletką po crowding (bez zwracania), dyskretna aktualizacja pozycji głosowaniem propozycji przywódców |

Wspólne dla wszystkich: to samo kodowanie, ten sam ewaluator, `populationSize` ocen na iterację,
wynik = zbiór wzajemnie niezdominowanych rozwiązań. Operatory NSGA: krzyżowanie jednopunktowe per
dron (p=0.9) i mutacja losowa genu (p=0.12) — `AbstractNsgaSolver`.

## Metryki (`planning.metrics`)

* `Hypervolume.of(points, ref)` — dokładna hiperobjętość (rekurencyjne cięcie wymiarów).
* `ObjectiveBounds.ofFronts(...)` + `Hypervolume.normalized(front, bounds, margin)` — wspólna
  normalizacja do `[0,1]` po sumie frontów wszystkich algorytmów, punkt referencyjny `1+margin`.

## Uruchamianie

```bash
./gradlew test                                   # testy jednostkowe
./gradlew runNsga2 | runNsga3 | runGwo           # aplikacja JavaFX z animacją
./gradlew runNsga3 -Ddrone.seed=42 -Ddrone.trajectory.csv=csv/trasa.csv   # odtwarzalnie + CSV
./gradlew runCompare -PcompareArgs="pop=100 iter=100 runs=5 seed=42 out=csv/comparison"
python3 scripts/plot_drone_trajectories_3d.py csv/trasa.csv --terrain
```

`runCompare` zapisuje `*_summary.csv` (run, algorytm, rozmiar frontu, HV, HV/max, czas) oraz
`*_fronts.csv` (wszystkie punkty frontów) do dalszej analizy.
