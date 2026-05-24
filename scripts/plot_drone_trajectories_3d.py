#!/usr/bin/env python3
"""
Wykres 3D tras dronów z CSV zapisanego przez TrajectoryCsvWriter (kolumny: step,drone_id,x,y,z).

Zależności: matplotlib
  pip install matplotlib

Przykład:
  python3 scripts/plot_drone_trajectories_3d.py trasa.csv
  python3 scripts/plot_drone_trajectories_3d.py trasa.csv -o trasa.png
  python3 scripts/plot_drone_trajectories_3d.py trasa.csv --z-aspect 0.45 --figsize 9 6
"""

from __future__ import annotations

import argparse
import csv
from collections import defaultdict

import matplotlib.pyplot as plt


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
    args = parser.parse_args()

    traces: dict[int, dict[str, list[int]]] = defaultdict(
        lambda: {"x": [], "y": [], "z": []}
    )

    with open(args.csv_file, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            d = int(row["drone_id"])
            traces[d]["x"].append(int(row["x"]))
            traces[d]["y"].append(int(row["y"]))
            traces[d]["z"].append(int(row["z"]))

    fig = plt.figure(figsize=tuple(args.figsize))
    ax = fig.add_subplot(projection="3d")

    all_x: list[int] = []
    all_y: list[int] = []
    all_z: list[int] = []

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
    ax.set_title("Trasy dronów (3D)")

    # Proporcje „pudła” 3D: szeroki XY, niższy Z — podobnie jak na dużym terenie (2. wykres)
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
