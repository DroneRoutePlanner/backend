#!/usr/bin/env python3
"""
Wykres 3D tras dronów z CSV zapisanego przez TrajectoryCsvWriter (kolumny: step,drone_id,x,y,z).

Zależności: matplotlib, numpy
  pip install matplotlib numpy

Przykład:
  python3 scripts/plot_drone_trajectories_3d.py trasa.csv
  python3 scripts/plot_drone_trajectories_3d.py trasa.csv -o trasa.png
  python3 scripts/plot_drone_trajectories_3d.py trasa.csv --factory-radars
  python3 scripts/plot_drone_trajectories_3d.py trasa.csv --terrain

Przy zapisie trajektorii z Java (drone.trajectory.csv) obok powstaje plik *_terrain.csv — flaga
--terrain wczytuje automatycznie trasa_terrain.csv przy wejściu trasa.csv.
"""

from __future__ import annotations

import argparse
import csv
from collections import defaultdict
from pathlib import Path

import matplotlib.pyplot as plt

try:
    import numpy as np
except ImportError as e:  # pragma: no cover
    raise SystemExit(
        "Brak pakietu numpy. Zainstaluj: pip install numpy"
    ) from e

# Jak PlanningScenarioFactory / PlanningContext dla domyślnego scenariusza
_TERRAIN_MAX_ALT = 12


def scaled_ground_z(raw: np.ndarray, max_altitude: int) -> np.ndarray:
    """Jak PlanningContext.scaledGroundLevel: raw * max_altitude / 100, obcięte do [0, max_altitude]."""
    z = (raw.astype(np.float64) * max_altitude / 100.0).round()
    return np.clip(z, 0, max_altitude)


def load_terrain_grid_csv(path: Path) -> np.ndarray:
    """
    Format z TerrainCsvWriter: pierwszy wiersz width,height, potem height wierszy × width kolumn raw 0..100.
    """
    lines = path.read_text(encoding="utf-8").strip().splitlines()
    if not lines:
        raise ValueError(f"Pusty plik: {path}")
    wh = lines[0].split(",")
    if len(wh) != 2:
        raise ValueError(f"Oczekiwano width,height w pierwszym wierszu, jest: {lines[0]!r}")
    w, h = int(wh[0].strip()), int(wh[1].strip())
    if len(lines) - 1 != h:
        raise ValueError(f"Oczekiwano {h} wierszy heightmapy, jest {len(lines) - 1}")
    hmap = np.zeros((w, h), dtype=np.int32)
    for yi, line in enumerate(lines[1 : h + 1]):
        parts = line.split(",")
        if len(parts) != w:
            raise ValueError(f"Wiersz y={yi}: oczekiwano {w} wartości, jest {len(parts)}")
        for xi, p in enumerate(parts):
            hmap[xi, yi] = int(p.strip())
    return hmap


def terrain_sibling_csv(trajectory_csv: Path) -> Path:
    return trajectory_csv.with_name(f"{trajectory_csv.stem}_terrain.csv")


def planning_factory_radars(map_w: int, map_h: int) -> list[tuple[float, float, float, float]]:
    """
    Te same wzory co w Java: PlanningScenarioFactory.defaultMultiDrone — RadarStation(...).
    Zwraca listę (x, y, z, influence_radius).
    """
    w, h = float(map_w), float(map_h)
    r0 = max(2.5, w * 0.20)
    r1 = max(2.5, w * 0.18)
    r2 = max(2.0, w * 0.16)
    return [
        (w * 0.28, h * 0.52, 5.0, r0),
        (w * 0.72, h * 0.32, 4.0, r1),
        (w * 0.52, h * 0.72, 6.0, r2),
    ]


def plot_radar_sphere(
    ax,
    cx: float,
    cy: float,
    cz: float,
    radius: float,
    *,
    color: str = "lightcoral",
    alpha: float = 0.16,
    resolution: int = 28,
) -> None:
    """Półprzezroczysta sfera: zasięg ryzyka = influenceRadius (poza kulą ryzyko = 0 w Java)."""
    u = np.linspace(0.0, 2.0 * np.pi, resolution)
    v = np.linspace(0.0, np.pi, resolution // 2)
    uu, vv = np.meshgrid(u, v)
    x = cx + radius * np.cos(uu) * np.sin(vv)
    y = cy + radius * np.sin(uu) * np.sin(vv)
    z = cz + radius * np.cos(vv)
    ax.plot_surface(
        x,
        y,
        z,
        color=color,
        alpha=alpha,
        linewidth=0,
        antialiased=True,
        shade=False,
        rstride=1,
        cstride=1,
    )


def plot_terrain_wireframe(ax, hmap_raw: np.ndarray) -> None:
    """Siatka 3D: wysokość Z w jednostkach jak trasy (scaled ground), indeks hmap [x,y] raw 0..100."""
    w, h = hmap_raw.shape
    x_idx = np.arange(w)
    y_idx = np.arange(h)
    X, Y = np.meshgrid(x_idx, y_idx, indexing="xy")
    Zmw = scaled_ground_z(hmap_raw, _TERRAIN_MAX_ALT).astype(np.float64)
    Z = Zmw.T
    stride = max(1, min(w, h) // 25)
    ax.plot_wireframe(
        X,
        Y,
        Z,
        rstride=stride,
        cstride=stride,
        color="cornflowerblue",
        linewidth=0.35,
        alpha=0.85,
    )


def parse_factory_dims(values: list[int] | None) -> tuple[int, int] | None:
    if values is None:
        return None
    if len(values) == 0:
        return (30, 30)
    if len(values) == 1:
        return (values[0], values[0])
    return (values[0], values[1])


def parse_radar_tuple(s: str) -> tuple[float, float, float, float]:
    parts = [p.strip() for p in s.split(",")]
    if len(parts) != 4:
        raise argparse.ArgumentTypeError(
            f"--radar wymaga x,y,z,promień (dostałem {s!r})"
        )
    return tuple(float(p) for p in parts)  # type: ignore[return-value]


def main() -> None:
    parser = argparse.ArgumentParser(description="Wykres 3D tras dronów z CSV.")
    parser.add_argument("csv_file", help="plik CSV z TrajectoryCsvWriter")
    parser.add_argument(
        "-o",
        "--output",
        metavar="PNG",
        help="zapis do pliku PNG zamiast okna interaktywnego",
    )
    parser.add_argument(
        "--figsize",
        nargs=2,
        type=float,
        default=(9.0, 6.5),
        metavar=("W", "H"),
        help="rozmiar figury w calach (domyślnie 9 6.5 — sensownie mieści się na ekranie)",
    )
    parser.add_argument(
        "--dpi",
        type=int,
        default=120,
        help="rozdzielczość przy zapisie PNG (domyślnie 120)",
    )
    parser.add_argument(
        "--z-aspect",
        type=float,
        default=0.42,
        metavar="F",
        help="jak „spłaszczyć” oś Z w pudełku 3D względem XY (0.3–0.5 jak szeroki teren na 2. wykresie; 1.0 = szczególnie wysoki Z)",
    )
    parser.add_argument(
        "--markers-max",
        type=int,
        default=80,
        metavar="N",
        help="co najwyżej N markerów na trasę (0 = bez markerów, tylko linia)",
    )
    parser.add_argument(
        "--factory-radars",
        nargs="*",
        type=int,
        metavar="N",
        help="narysuj sfery zasięgu jak w PlanningScenarioFactory (opcjonalnie szerokość wysokość mapy; bez argumentów = 30×30)",
    )
    parser.add_argument(
        "--radar",
        action="append",
        default=[],
        type=parse_radar_tuple,
        metavar="X,Y,Z,R",
        help="dodatkowa sfera: środek (x,y,z) i promień w tych samych jednostkach co CSV (powtarzalne)",
    )
    parser.add_argument(
        "--radar-alpha",
        type=float,
        default=0.16,
        help="przezroczystość sfer radarów 0–1 (domyślnie 0.16)",
    )
    parser.add_argument(
        "--radar-color",
        type=str,
        default="lightcoral",
        help="kolor wypełnienia sfer (matplotlib, domyślnie bladoczerwony: lightcoral)",
    )
    parser.add_argument(
        "--terrain",
        action="store_true",
        help="narysuj teren z pliku <nazwa_trasy>_terrain.csv obok CSV (zapis z Java przy trajektorii)",
    )
    args = parser.parse_args()

    traj_path = Path(args.csv_file)
    terrain_hmap: np.ndarray | None = None
    if args.terrain:
        tp = terrain_sibling_csv(traj_path)
        if not tp.is_file():
            raise SystemExit(
                f"Brak pliku terenu {tp} — uruchom symulację z zapisem CSV (Java zapisuje *_terrain.csv obok trasy)."
            )
        terrain_hmap = load_terrain_grid_csv(tp)

    traces: dict[int, dict[str, list[int]]] = defaultdict(
        lambda: {"x": [], "y": [], "z": []}
    )

    with open(traj_path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            d = int(row["drone_id"])
            traces[d]["x"].append(int(row["x"]))
            traces[d]["y"].append(int(row["y"]))
            traces[d]["z"].append(int(row["z"]))

    fig = plt.figure(figsize=tuple(args.figsize))
    ax = fig.add_subplot(projection="3d")

    all_x: list[float] = []
    all_y: list[float] = []
    all_z: list[float] = []

    if terrain_hmap is not None:
        plot_terrain_wireframe(ax, terrain_hmap)
        w0, h0 = terrain_hmap.shape
        z_disp = scaled_ground_z(terrain_hmap, _TERRAIN_MAX_ALT)
        all_x.extend([0, w0 - 1])
        all_y.extend([0, h0 - 1])
        all_z.extend([float(z_disp.min()), float(z_disp.max())])

    dims = parse_factory_dims(args.factory_radars)
    radars: list[tuple[float, float, float, float]] = []
    if dims is not None:
        radars.extend(planning_factory_radars(dims[0], dims[1]))
    radars.extend(args.radar)

    for cx, cy, cz, rr in radars:
        plot_radar_sphere(
            ax,
            cx,
            cy,
            cz,
            rr,
            color=args.radar_color,
            alpha=float(args.radar_alpha),
        )
        all_x.extend((cx - rr, cx + rr))
        all_y.extend((cy - rr, cy + rr))
        all_z.extend((cz - rr, cz + rr))

    for d in sorted(traces):
        t = traces[d]
        xs, ys, zs = t["x"], t["y"], t["z"]
        all_x.extend(xs)
        all_y.extend(ys)
        all_z.extend(zs)

        n = len(xs)
        if n == 0:
            continue
        if args.markers_max <= 0:
            markevery = None
            marker = None
        else:
            step_m = max(1, n // args.markers_max)
            markevery = step_m
            marker = "o"

        ax.plot(
            xs,
            ys,
            zs,
            label=f"Dron {d}",
            marker=marker,
            markersize=2.2,
            markevery=markevery,
            linewidth=1.1,
        )

    ax.set_xlabel("X (komórki)")
    ax.set_ylabel("Y (komórki)")
    ax.set_zlabel("Z (komórki)")
    ax.legend(loc="upper left", fontsize=9)
    title = "Trasy dronów (3D)"
    bits = []
    if terrain_hmap is not None:
        bits.append("teren")
    if radars:
        bits.append("radary")
    if bits:
        title += " — " + ", ".join(bits)
    ax.set_title(title)

    xr = (max(all_x) - min(all_x)) if all_x else 1
    yr = (max(all_y) - min(all_y)) if all_y else 1
    zr = (max(all_z) - min(all_z)) if all_z else 1
    xr = max(xr, 1)
    yr = max(yr, 1)
    zr = max(zr, 1)
    z_vis = max(zr * float(args.z_aspect), 0.5)
    try:
        ax.set_box_aspect((xr, yr, z_vis))
    except AttributeError:
        pass

    fig.tight_layout()

    if args.output:
        plt.savefig(args.output, dpi=args.dpi, bbox_inches="tight")
        print(f"Zapisano: {args.output}")
    else:
        plt.show()


if __name__ == "__main__":
    main()
