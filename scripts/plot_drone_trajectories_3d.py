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

import matplotlib.colors as mcolors
import matplotlib.pyplot as plt

try:
    import numpy as np
except ImportError as e:  # pragma: no cover
    raise SystemExit(
        "Brak pakietu numpy. Zainstaluj: pip install numpy"
    ) from e

# Jak PlanningScenarioFactory / PlanningContext dla domyślnego scenariusza
_TERRAIN_MAX_ALT = 20


#: Kolory markerów początku i końca trasy (dobrane tak, by nie myliły się
#: z czerwienią radarów ani z domyślną paletą linii tras).
_START_COLOR = "#00C853"
_END_COLOR = "#AA00FF"


def scaled_ground_z(raw: np.ndarray, max_altitude: int) -> np.ndarray:
    """
    Dokładnie jak PlanningContext.scaledGroundLevel w Javie:
    dzielenie CAŁKOWITE raw * max_altitude / 100 (obcięcie w dół, nie zaokrąglenie),
    a następnie obcięcie do [0, max_altitude].

    Zaokrąglanie zamiast obcinania rysowałoby teren i radary o 1 jednostkę za wysoko,
    przez co trasy wyglądałyby, jakby przecinały zbocze, choć symulacja tego nie dopuszcza.
    """
    z = np.floor_divide(raw.astype(np.int64) * int(max_altitude), 100)
    return np.clip(z, 0, max_altitude).astype(np.float64)


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


def planning_factory_radars(
    map_w: int,
    map_h: int,
    hmap_raw: np.ndarray | None = None,
) -> list[tuple[float, float, float, float]]:
    """
    Te same wzory co w Java: PlanningScenarioFactory.defaultMultiDrone — RadarStation(...).
    Zwraca listę (x, y, z, influence_radius). Gdy podano hmap_raw, z = wysokość terenu w komórce.
    """
    w, h = float(map_w), float(map_h)
    specs = [
        (w * 0.28, h * 0.52, max(2.5, w * 0.20)),
        (w * 0.72, h * 0.32, max(2.5, w * 0.18)),
        (w * 0.52, h * 0.72, max(2.0, w * 0.16)),
    ]
    radars: list[tuple[float, float, float, float]] = []
    for x, y, radius in specs:
        if hmap_raw is not None:
            ix = int(round(x))
            iy = int(round(y))
            ix = max(0, min(hmap_raw.shape[0] - 1, ix))
            iy = max(0, min(hmap_raw.shape[1] - 1, iy))
            z = float(scaled_ground_z(hmap_raw[ix : ix + 1, iy : iy + 1], _TERRAIN_MAX_ALT)[0, 0])
        else:
            z = 0.0
        radars.append((x, y, z, radius))
    return radars


def plot_radar_sphere(
    ax,
    cx: float,
    cy: float,
    cz: float,
    radius: float,
    *,
    hmap_raw: np.ndarray | None = None,
    color: str = "#FF4444",
    alpha: float = 0.18,
    edge_color: str = "#FF0000",
    line_width: float = 2.8,
    resolution: int = 72,
) -> None:
    """
    Sfera zasięgu radarowego: lekkie półprzezroczyste wypełnienie + gruby czerwony obrys (linie 3D).
    """
    del hmap_raw, color

    u = np.linspace(0.0, 2.0 * np.pi, resolution)
    v = np.linspace(0.0, np.pi, max(18, resolution // 3))
    uu, vv = np.meshgrid(u, v)
    x = cx + radius * np.cos(uu) * np.sin(vv)
    y = cy + radius * np.sin(uu) * np.sin(vv)
    z = cz + radius * np.cos(vv)

    fill_rgba = mcolors.to_rgba("#FF5555", alpha=alpha)
    ax.plot_surface(
        x,
        y,
        z,
        color=fill_rgba,
        linewidth=0,
        antialiased=False,
        shade=False,
        rstride=4,
        cstride=4,
    )

    theta = np.linspace(0.0, 2.0 * np.pi, 120)
    phi = np.linspace(0.0, np.pi, 72)

    lw = float(line_width)
    for z_frac in (0.0, 0.25, 0.5, 0.75, 0.92, 1.0):
        zc = cz + radius * z_frac
        r_xy = radius * np.sqrt(max(0.0, 1.0 - z_frac * z_frac))
        ax.plot(
            cx + r_xy * np.cos(theta),
            cy + r_xy * np.sin(theta),
            np.full_like(theta, zc),
            color=edge_color,
            linewidth=lw if z_frac >= 0.5 else lw * 0.85,
            alpha=0.65,
            zorder=2,
        )

    for az in np.linspace(0.0, 2.0 * np.pi, 10, endpoint=False):
        ax.plot(
            cx + radius * np.sin(phi) * np.cos(az),
            cy + radius * np.sin(phi) * np.sin(az),
            cz + radius * np.cos(phi),
            color=edge_color,
            linewidth=lw * 0.9,
            alpha=0.65,
            zorder=2,
        )

    ax.scatter([cx], [cy], [cz], color=edge_color, s=70, depthshade=False, zorder=2)


def plot_terrain_surface(ax, hmap_raw: np.ndarray, *, alpha: float = 0.97):
    """Powierzchnia 3D pokolorowana wysokością (góry widoczne na PNG)."""
    w, h = hmap_raw.shape
    x_idx = np.arange(w)
    y_idx = np.arange(h)
    X, Y = np.meshgrid(x_idx, y_idx, indexing="xy")
    Z = scaled_ground_z(hmap_raw, _TERRAIN_MAX_ALT).astype(np.float64).T
    z_min = float(Z.min())
    z_max = float(Z.max())
    if z_max <= z_min:
        z_max = z_min + 1.0
    return ax.plot_surface(
        X,
        Y,
        Z,
        cmap="terrain",
        vmin=z_min,
        vmax=z_max,
        linewidth=0.3,
        edgecolor=(0.35, 0.28, 0.22, 0.45),
        alpha=alpha,
        antialiased=True,
        shade=True,
        rcount=min(w, 80),
        ccount=min(h, 80),
        zorder=1,
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
        default=0.52,
        metavar="F",
        help="jak „spłaszczyć” oś Z w pudełku 3D względem XY (0.3–0.5 jak szeroki teren; 1.0 = pełna wysokość Z)",
    )
    parser.add_argument(
        "--markers-max",
        type=int,
        default=80,
        metavar="N",
        help="co najwyżej N markerów na trasę (0 = bez markerów, tylko linia)",
    )
    parser.add_argument(
        "--no-endpoints",
        action="store_true",
        help="nie zaznaczaj punktów startowych i docelowych dronów",
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
        default=0.18,
        help="przezroczystość wypełnienia sfery radarów 0–1 (domyślnie 0.18; obrys zawsze pełny)",
    )
    parser.add_argument(
        "--radar-color",
        type=str,
        default="#FF4444",
        help="kolor wypełnienia sfer (domyślnie #FF4444)",
    )
    parser.add_argument(
        "--radar-edge-color",
        type=str,
        default="#FF0000",
        help="kolor obrysu kopuły i markera środka (domyślnie #FF0000)",
    )
    parser.add_argument(
        "--terrain-alpha",
        type=float,
        default=None,
        help="przezroczystość terenu 0–1 (domyślnie 0.82 z radarami, 0.97 bez)",
    )
    parser.add_argument(
        "--radar-linewidth",
        type=float,
        default=1.6,
        help="grubość czerwonego obrysu sfery radarowej (domyślnie 1.6)",
    )
    parser.add_argument(
        "--view",
        choices=["3d", "top"],
        default="3d",
        help="3d (domyślnie) lub top — rzut z góry (oś Z prostopadła do kartki)",
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
    # Jawne warstwy zamiast sortowania po głębi: trasy zawsze na wierzchu (sfery radarów
    # zasłaniałyby trasy przelatujące NAD nimi, co sugerowałoby wejście w strefę wykrycia).
    ax.computed_zorder = False

    all_x: list[float] = []
    all_y: list[float] = []
    all_z: list[float] = []

    dims = parse_factory_dims(args.factory_radars)
    radars: list[tuple[float, float, float, float]] = []
    if dims is not None:
        radars.extend(planning_factory_radars(dims[0], dims[1], terrain_hmap))
    radars.extend(args.radar)

    terrain_alpha = args.terrain_alpha
    if terrain_alpha is None:
        terrain_alpha = 0.58 if radars else 0.97

    terrain_surf = None
    if terrain_hmap is not None:
        terrain_surf = plot_terrain_surface(ax, terrain_hmap, alpha=float(terrain_alpha))
        w0, h0 = terrain_hmap.shape
        z_disp = scaled_ground_z(terrain_hmap, _TERRAIN_MAX_ALT)
        all_x.extend([0, w0 - 1])
        all_y.extend([0, h0 - 1])
        all_z.extend([float(z_disp.min()), float(z_disp.max())])

    # Etykiety legendy dla start/cel dodajemy przy ostatniej trasie, żeby w legendzie
    # znalazły się pod pozycjami dronów, a nie pomiędzy nimi.
    last_drone = sorted(traces)[-1] if traces else None
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
            linewidth=1.3,
            zorder=5,
        )

        if not args.no_endpoints:
            first = d == last_drone
            ax.scatter(
                [xs[0]], [ys[0]], [zs[0]],
                marker="o",
                s=120,
                c=_START_COLOR,
                edgecolors="black",
                linewidths=1.2,
                depthshade=False,
                zorder=7,
                label="Punkt startowy" if first else None,
            )
            ax.scatter(
                [xs[-1]], [ys[-1]], [zs[-1]],
                marker="*",
                s=300,
                c=_END_COLOR,
                edgecolors="black",
                linewidths=1.0,
                depthshade=False,
                zorder=7,
                label="Punkt końcowy" if first else None,
            )

    for cx, cy, cz, rr in radars:
        plot_radar_sphere(
            ax,
            cx,
            cy,
            cz,
            rr,
            hmap_raw=terrain_hmap,
            color=args.radar_color,
            alpha=float(args.radar_alpha),
            edge_color=args.radar_edge_color,
            line_width=float(args.radar_linewidth),
        )
        all_x.extend((cx - rr, cx + rr))
        all_y.extend((cy - rr, cy + rr))
        all_z.extend((cz - rr, cz + rr))

    ax.set_xlabel("X (komórki)")
    ax.set_ylabel("Y (komórki)")
    ax.set_zlabel("Z (komórki)")
    ax.legend(loc="upper left", fontsize=9)
    title = "Trasy dronów (widok z góry)" if args.view == "top" else "Trasy dronów (3D)"
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

    if args.view == "top":
        ax.view_init(elev=90, azim=-90)
        ax.set_zticks([])
        ax.set_zlabel("")
    elif radars:
        ax.view_init(elev=31, azim=-56)

    if terrain_surf is not None:
        cbar = fig.colorbar(terrain_surf, ax=ax, shrink=0.55, pad=0.08)
        cbar.set_label("Wysokość terenu (Z)")

    fig.tight_layout()

    if args.output:
        plt.savefig(args.output, dpi=args.dpi, bbox_inches="tight", facecolor="white")
        print(f"Zapisano: {args.output}")
    else:
        plt.show()


if __name__ == "__main__":
    main()
