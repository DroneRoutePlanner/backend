#!/usr/bin/env python3
"""
Analiza statystyczna porównania NSGA-II / NSGA-III / MOGWO metryką hiperobjętości.

Wejście: pliki *_summary.csv, *_fronts.csv, *_convergence.csv zapisane przez
org.example.bench.AlgorithmComparison. Wyjście: tabele LaTeX (*.tex), rysunki
(PNG + PDF) oraz summary.json z wszystkimi wyliczonymi wartościami.

Zależności: numpy, matplotlib (bez scipy — testy zaimplementowano wprost).

Przykład:
  python3 scripts/analyze_comparison.py --main experiments/results/main \
      --budget 100=experiments/results/budget100 --budget 250=experiments/results/budget250 \
      --budget 500=experiments/results/main --budget 1000=experiments/results/budget1000 \
      --out experiments/analysis
"""

from __future__ import annotations

import argparse
import csv
import json
import math
from collections import defaultdict
from itertools import combinations
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402

ALGORITHMS = ["NSGA-II", "NSGA-III", "MOGWO"]
# Kolor podąża za algorytmem (stałe przypisanie, sloty 1–3 palety referencyjnej).
COLORS = {"NSGA-II": "#2a78d6", "NSGA-III": "#eb6834", "MOGWO": "#1baf7a"}
INK = "#0b0b0b"
INK_SECONDARY = "#52514e"
INK_MUTED = "#898781"
GRID = "#e1e0d9"
AXIS = "#c3c2b7"
SURFACE = "#ffffff"


# ------------------------------------------------------------- hiperobjętość
def nondominated(points: list[np.ndarray]) -> list[np.ndarray]:
    result = []
    for i, p in enumerate(points):
        dominated = False
        for j, q in enumerate(points):
            if i == j:
                continue
            if np.all(q <= p) and np.any(q < p):
                dominated = True
                break
            if j < i and np.array_equal(q, p):
                dominated = True
                break
        if not dominated:
            result.append(p)
    return result


def hypervolume(points: list[np.ndarray], reference: np.ndarray) -> float:
    """Dokładna hiperobjętość (minimalizacja) — rekurencyjne cięcie wymiarów, jak w klasie Java Hypervolume."""
    inside = [p for p in points if np.all(p < reference)]
    return _volume(nondominated(inside), reference, reference.size)


def _volume(points: list[np.ndarray], reference: np.ndarray, dim: int) -> float:
    if not points:
        return 0.0
    if dim == 1:
        return float(reference[0] - min(p[0] for p in points))
    last = dim - 1
    points = sorted(points, key=lambda p: p[last])
    total = 0.0
    projected: list[np.ndarray] = []
    for i, p in enumerate(points):
        projected.append(p[:last])
        top = points[i + 1][last] if i + 1 < len(points) else reference[last]
        thickness = top - p[last]
        if thickness > 0:
            total += thickness * _volume(nondominated(projected), reference, last)
    return total


# --------------------------------------------------------------------------- I/O
def read_csv(path: Path) -> list[dict[str, str]]:
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def load_prefix(prefix: str, max_runs: int | None = None) -> dict:
    p = Path(prefix)
    summary = read_csv(Path(str(p) + "_summary.csv"))
    fronts = read_csv(Path(str(p) + "_fronts.csv"))
    conv_path = Path(str(p) + "_convergence.csv")
    convergence = read_csv(conv_path) if conv_path.is_file() else []
    if max_runs is not None:
        summary = [r for r in summary if int(r["run"]) < max_runs]
        fronts = [r for r in fronts if int(r["run"]) < max_runs]
        convergence = [r for r in convergence if int(r["run"]) < max_runs]
    runs = sorted({int(r["run"]) for r in summary})
    hv = {a: np.array([float(r["hypervolume_ratio"]) for r in summary if r["algorithm"] == a]) for a in ALGORITHMS}
    front_size = {a: np.array([int(r["front_size"]) for r in summary if r["algorithm"] == a]) for a in ALGORITHMS}
    time_ms = {a: np.array([int(r["time_ms"]) for r in summary if r["algorithm"] == a]) for a in ALGORITHMS}
    points = defaultdict(list)  # (run, alg) -> list of (makespan, energy, risk)
    for r in fronts:
        points[(int(r["run"]), r["algorithm"])].append(
            (float(r["makespan"]), float(r["energy"]), float(r["radar_risk"]))
        )
    return {
        "runs": runs,
        "hv": hv,
        "front_size": front_size,
        "time_ms": time_ms,
        "points": points,
        "convergence": convergence,
    }


# ------------------------------------------------------------------- statystyka
def describe(x: np.ndarray) -> dict:
    return {
        "n": int(x.size),
        "mean": float(np.mean(x)),
        "sd": float(np.std(x, ddof=1)) if x.size > 1 else 0.0,
        "median": float(np.median(x)),
        "q1": float(np.percentile(x, 25)),
        "q3": float(np.percentile(x, 75)),
        "min": float(np.min(x)),
        "max": float(np.max(x)),
    }


def average_ranks(values: np.ndarray, descending: bool = True) -> np.ndarray:
    """Rangi z uśrednianiem remisów; ranga 1 = największa wartość (descending)."""
    order = -values if descending else values
    sorter = np.argsort(order, kind="mergesort")
    ranks = np.empty(len(values), dtype=float)
    i = 0
    while i < len(values):
        j = i
        while j + 1 < len(values) and order[sorter[j + 1]] == order[sorter[i]]:
            j += 1
        ranks[sorter[i : j + 1]] = (i + j) / 2.0 + 1.0
        i = j + 1
    return ranks


def chi2_sf_df2(x: float) -> float:
    """Funkcja przeżycia rozkładu chi-kwadrat o 2 stopniach swobody (postać zamknięta)."""
    return math.exp(-x / 2.0)


def friedman_test(matrix: np.ndarray) -> dict:
    """matrix: n_runs × k algorytmów (większe = lepsze). Test Friedmana z korektą na remisy."""
    n, k = matrix.shape
    assert k == 3, "p-wartość w postaci zamkniętej zaimplementowano dla k = 3 (df = 2)"
    ranks = np.vstack([average_ranks(row) for row in matrix])
    mean_ranks = ranks.mean(axis=0)
    rank_sums = ranks.sum(axis=0)
    stat = 12.0 / (n * k * (k + 1)) * float(np.sum(rank_sums**2)) - 3.0 * n * (k + 1)
    # korekta na remisy
    tie_term = 0.0
    for row in ranks:
        _, counts = np.unique(row, return_counts=True)
        tie_term += float(np.sum(counts**3 - counts))
    denom = 1.0 - tie_term / (n * (k**3 - k))
    if denom > 0:
        stat /= denom
    return {"statistic": float(stat), "p_value": chi2_sf_df2(stat), "mean_ranks": mean_ranks.tolist(), "ranks": ranks}


def wilcoxon_signed_rank(x: np.ndarray, y: np.ndarray) -> dict:
    """Dwustronny test Wilcoxona dla par; dokładny rozkład (bez remisów, n ≤ 30) lub aproksymacja normalna."""
    d = x - y
    d = d[d != 0.0]
    n = d.size
    if n == 0:
        return {"n": 0, "W": 0.0, "p_value": 1.0, "method": "brak różnic"}
    ranks = average_ranks(np.abs(d), descending=False)
    w_plus = float(np.sum(ranks[d > 0]))
    w_minus = float(np.sum(ranks[d < 0]))
    w = min(w_plus, w_minus)
    has_ties = len(np.unique(np.abs(d))) < n
    if not has_ties and n <= 30:
        # dokładny rozkład statystyki: liczba podzbiorów rang o danej sumie
        max_sum = n * (n + 1) // 2
        counts = np.zeros(max_sum + 1, dtype=np.float64)
        counts[0] = 1.0
        for r in range(1, n + 1):
            counts[r:] = counts[r:] + counts[:-r].copy()
        total = 2.0**n
        cdf = float(np.sum(counts[: int(w) + 1])) / total
        p = min(1.0, 2.0 * cdf)
        method = "dokładny"
    else:
        mean = n * (n + 1) / 4.0
        _, t = np.unique(np.abs(d), return_counts=True)
        var = n * (n + 1) * (2 * n + 1) / 24.0 - float(np.sum(t**3 - t)) / 48.0
        z = (w - mean + 0.5) / math.sqrt(var)
        p = min(1.0, 2.0 * 0.5 * math.erfc(-z / math.sqrt(2.0)))
        method = "aproksymacja normalna"
    return {"n": int(n), "W": w, "W_plus": w_plus, "W_minus": w_minus, "p_value": float(p), "method": method}


def holm_correction(p_values: list[float]) -> list[float]:
    m = len(p_values)
    order = sorted(range(m), key=lambda i: p_values[i])
    adjusted = [0.0] * m
    running = 0.0
    for rank, i in enumerate(order):
        running = max(running, (m - rank) * p_values[i])
        adjusted[i] = min(1.0, running)
    return adjusted


def cliffs_delta(x: np.ndarray, y: np.ndarray) -> tuple[float, str]:
    diff = x[:, None] - y[None, :]
    delta = float((np.sum(diff > 0) - np.sum(diff < 0)) / (x.size * y.size))
    a = abs(delta)
    magnitude = "pomijalny" if a < 0.147 else "mały" if a < 0.33 else "średni" if a < 0.474 else "duży"
    return delta, magnitude


# ------------------------------------------------------------------- LaTeX
def fmt(x: float, digits: int = 3) -> str:
    return f"{x:.{digits}f}".replace(".", "{,}")


def fmt_p(p: float) -> str:
    if p < 0.001:
        return "$< 0{,}001$"
    return "$" + fmt(p, 3) + "$"


def write_tex(path: Path, header: list[str], rows: list[list[str]], caption: str, label: str, align: str) -> None:
    lines = [
        "\\begin{table}[htbp]",
        "    \\centering",
        f"    \\caption{{{caption}}}",
        f"    \\label{{{label}}}",
        f"    \\begin{{tabular}}{{{align}}}",
        "        \\hline",
        "        " + " & ".join(header) + " \\\\",
        "        \\hline",
    ]
    for row in rows:
        lines.append("        " + " & ".join(row) + " \\\\")
    lines += ["        \\hline", "    \\end{tabular}", "\\end{table}", ""]
    path.write_text("\n".join(lines), encoding="utf-8")


# ------------------------------------------------------------------- rysunki
def style_axes(ax) -> None:
    ax.set_facecolor(SURFACE)
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    for side in ("left", "bottom"):
        ax.spines[side].set_color(AXIS)
        ax.spines[side].set_linewidth(0.8)
    ax.tick_params(colors=INK_SECONDARY, labelsize=9, length=3, width=0.8)
    ax.yaxis.grid(True, color=GRID, linewidth=0.8)
    ax.set_axisbelow(True)
    ax.xaxis.label.set_color(INK)
    ax.yaxis.label.set_color(INK)


def save(fig, out: Path, name: str) -> None:
    fig.savefig(out / f"{name}.png", dpi=200, bbox_inches="tight", facecolor=SURFACE)
    fig.savefig(out / f"{name}.pdf", bbox_inches="tight", facecolor=SURFACE)
    plt.close(fig)


def fig_boxplot(main: dict, out: Path) -> None:
    fig, ax = plt.subplots(figsize=(6.2, 3.8))
    style_axes(ax)
    data = [main["hv"][a] for a in ALGORITHMS]
    positions = np.arange(len(ALGORITHMS))
    bp = ax.boxplot(data, positions=positions, widths=0.42, patch_artist=True, showfliers=False,
                    medianprops={"color": INK, "linewidth": 1.6},
                    whiskerprops={"color": AXIS, "linewidth": 1.0},
                    capprops={"color": AXIS, "linewidth": 1.0},
                    boxprops={"linewidth": 1.0})
    for patch, a in zip(bp["boxes"], ALGORITHMS):
        patch.set_facecolor(COLORS[a])
        patch.set_alpha(0.18)
        patch.set_edgecolor(COLORS[a])
    rng = np.random.default_rng(7)
    for i, a in enumerate(ALGORITHMS):
        x = positions[i] + rng.uniform(-0.13, 0.13, size=main["hv"][a].size)
        ax.scatter(x, main["hv"][a], s=26, color=COLORS[a], edgecolor=SURFACE, linewidth=0.8, zorder=3)
    ax.set_xticks(positions)
    ax.set_xticklabels([f"{a}\n(n = {main['hv'][a].size})" for a in ALGORITHMS], color=INK)
    ax.set_ylabel("Znormalizowana hiperobjętość HV / HV$_{\\max}$")
    ax.set_ylim(0, 1.0)
    save(fig, out, "fig_hv_boxplot")


def fig_convergence(main: dict, out: Path) -> dict:
    conv = defaultdict(lambda: defaultdict(list))  # alg -> iteration -> [hv over runs]
    for r in main["convergence"]:
        conv[r["algorithm"]][int(r["iteration"])].append(float(r["hypervolume_ratio"]))
    fig, ax = plt.subplots(figsize=(6.6, 3.8))
    style_axes(ax)
    curves = {}
    for a in ALGORITHMS:
        iters = np.array(sorted(conv[a]))
        mean = np.array([np.mean(conv[a][i]) for i in iters])
        sd = np.array([np.std(conv[a][i], ddof=1) if len(conv[a][i]) > 1 else 0.0 for i in iters])
        curves[a] = {"iterations": iters.tolist(), "mean": mean.tolist(), "sd": sd.tolist()}
        ax.fill_between(iters, mean - sd, mean + sd, color=COLORS[a], alpha=0.10, linewidth=0)
        ax.plot(iters, mean, color=COLORS[a], linewidth=2.0, solid_capstyle="round", label=a)
        ax.scatter([iters[-1]], [mean[-1]], s=40, color=COLORS[a], edgecolor=SURFACE, linewidth=1.2, zorder=4)
        ax.annotate(f"{mean[-1]:.2f}".replace(".", ","), (iters[-1], mean[-1]), xytext=(6, 0),
                    textcoords="offset points", fontsize=8.5, color=INK_SECONDARY, va="center")
    ax.set_xlabel("Iteracja")
    ax.set_ylabel("HV / HV$_{\\max}$ (średnia ± odch. std.)")
    ax.set_xlim(0, max(max(c["iterations"]) for c in curves.values()) * 1.08)
    ax.set_ylim(0, 1.0)
    ax.legend(frameon=False, fontsize=9, loc="upper left")
    save(fig, out, "fig_convergence")
    return curves


def budget_hv_common(budgets: dict[int, dict], margin: float = 0.1) -> dict[int, dict[str, np.ndarray]]:
    """HV/HV_max z granicami wspólnymi dla instancji po WSZYSTKICH budżetach i algorytmach —
    dzięki temu wartości są porównywalne między budżetami (w przeciwieństwie do normalizacji per budżet)."""
    runs = sorted(set.intersection(*(set(d["runs"]) for d in budgets.values())))
    ref = np.full(3, 1.0 + margin)
    max_hv = float((1.0 + margin) ** 3)
    result = {b: {a: [] for a in ALGORITHMS} for b in budgets}
    for run in runs:
        union = np.array([pt for d in budgets.values() for a in ALGORITHMS for pt in d["points"][(run, a)]])
        ideal, nadir = union.min(axis=0), union.max(axis=0)
        span = np.where(nadir - ideal < 1e-12, 1.0, nadir - ideal)
        for b, d in budgets.items():
            for a in ALGORITHMS:
                pts = [(np.array(pt) - ideal) / span for pt in d["points"][(run, a)]]
                result[b][a].append(hypervolume(pts, ref) / max_hv)
    return {b: {a: np.array(v) for a, v in per.items()} for b, per in result.items()}


def fig_budget(budget_hv: dict[int, dict[str, np.ndarray]], out: Path, population: int = 100) -> None:
    labels = sorted(budget_hv)
    fig, ax = plt.subplots(figsize=(6.2, 3.8))
    style_axes(ax)
    x = np.arange(len(labels))
    for i, a in enumerate(ALGORITHMS):
        means = [np.mean(budget_hv[b][a]) for b in labels]
        sds = [np.std(budget_hv[b][a], ddof=1) for b in labels]
        offset = (i - 1) * 0.06
        ax.errorbar(x + offset, means, yerr=sds, color=COLORS[a], linewidth=2.0, capsize=3,
                    marker="o", markersize=6, markeredgecolor=SURFACE, markeredgewidth=1.2, label=a)
    ax.set_xticks(x)
    ax.set_xticklabels([str(b) for b in labels], color=INK)
    ax.set_xlabel(f"Liczba iteracji (budżet = {population} ocen × iteracje)")
    ax.set_ylabel("HV / HV$_{\\max}$ (wspólna normalizacja)")
    ax.set_ylim(0, 1.0)
    ax.legend(frameon=False, fontsize=9, loc="upper left")
    save(fig, out, "fig_budget")


def fig_fronts(main: dict, run: int, out: Path) -> None:
    pairs = [((0, 1), "Makespan [tiki]", "Energia"), ((0, 2), "Makespan [tiki]", "Ryzyko radarowe"),
             ((1, 2), "Energia", "Ryzyko radarowe")]
    fig, axes = plt.subplots(1, 3, figsize=(11, 3.6))
    for ax, ((i, j), xl, yl) in zip(axes, pairs):
        style_axes(ax)
        ax.xaxis.grid(True, color=GRID, linewidth=0.8)
        for a in ALGORITHMS:
            pts = np.array(main["points"][(run, a)])
            ax.scatter(pts[:, i], pts[:, j], s=30, color=COLORS[a], edgecolor=SURFACE, linewidth=0.8,
                       alpha=0.9, label=a)
        ax.set_xlabel(xl)
        ax.set_ylabel(yl)
    axes[0].legend(frameon=False, fontsize=9)
    fig.suptitle(f"Fronty końcowe — instancja {run + 1} (rzuty par kryteriów)", fontsize=10, color=INK)
    fig.tight_layout()
    save(fig, out, "fig_fronts_projections")


def fig_objectives(best: dict, out: Path) -> None:
    names = [("makespan", "Najlepszy makespan [tiki]"), ("energy", "Najlepsza energia"),
             ("zero_risk_pct", "Udział rozwiązań o zerowym ryzyku [%]")]
    fig, axes = plt.subplots(1, 3, figsize=(11, 3.6))
    rng = np.random.default_rng(11)
    for ax, (key, title) in zip(axes, names):
        style_axes(ax)
        data = [best[a][key] for a in ALGORITHMS]
        positions = np.arange(len(ALGORITHMS))
        bp = ax.boxplot(data, positions=positions, widths=0.42, patch_artist=True, showfliers=False,
                        medianprops={"color": INK, "linewidth": 1.6},
                        whiskerprops={"color": AXIS, "linewidth": 1.0},
                        capprops={"color": AXIS, "linewidth": 1.0})
        for patch, a in zip(bp["boxes"], ALGORITHMS):
            patch.set_facecolor(COLORS[a])
            patch.set_alpha(0.18)
            patch.set_edgecolor(COLORS[a])
        for i, a in enumerate(ALGORITHMS):
            x = positions[i] + rng.uniform(-0.13, 0.13, size=len(best[a][key]))
            ax.scatter(x, best[a][key], s=22, color=COLORS[a], edgecolor=SURFACE, linewidth=0.8, zorder=3)
        ax.set_xticks(positions)
        ax.set_xticklabels(ALGORITHMS, color=INK, fontsize=9)
        ax.set_title(title, fontsize=10, color=INK)
    fig.tight_layout()
    save(fig, out, "fig_best_objectives")


# ------------------------------------------------------------------- analiza
def main() -> None:
    parser = argparse.ArgumentParser(description="Analiza porównania algorytmów (hiperobjętość).")
    parser.add_argument("--main", required=True, help="prefiks plików eksperymentu głównego")
    parser.add_argument("--budget", action="append", default=[], metavar="ITER=PREFIX",
                        help="eksperymenty wrażliwości na budżet (powtarzalne)")
    parser.add_argument("--out", default="experiments/analysis")
    parser.add_argument("--population", type=int, default=100,
                        help="liczebność populacji użyta w serii budżetowej (tylko do opisu osi)")
    parser.add_argument("--front-run", type=int, default=0, help="instancja do rysunku rzutów frontów")
    parser.add_argument("--budget-runs", type=int, default=10, help="liczba instancji w analizie budżetu")
    args = parser.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    main_data = load_prefix(args.main)
    n_runs = len(main_data["runs"])
    summary: dict = {"n_runs": n_runs, "algorithms": ALGORITHMS}

    # --- opis HV
    summary["hv"] = {a: describe(main_data["hv"][a]) for a in ALGORITHMS}
    matrix = np.column_stack([main_data["hv"][a] for a in ALGORITHMS])
    fried = friedman_test(matrix)
    ranks = fried.pop("ranks")
    wins = {a: int(np.sum(ranks[:, i] == 1.0)) for i, a in enumerate(ALGORITHMS)}
    summary["friedman"] = fried
    summary["mean_rank"] = dict(zip(ALGORITHMS, fried["mean_ranks"]))
    summary["wins"] = wins

    # --- testy parami
    pairwise = []
    for a, b in combinations(ALGORITHMS, 2):
        x, y = main_data["hv"][a], main_data["hv"][b]
        w = wilcoxon_signed_rank(x, y)
        delta, magnitude = cliffs_delta(x, y)
        d = x - y
        pairwise.append({
            "a": a, "b": b, "median_diff": float(np.median(d)), "mean_diff": float(np.mean(d)),
            "wins_a": int(np.sum(d > 0)), "wins_b": int(np.sum(d < 0)), "ties": int(np.sum(d == 0)),
            "wilcoxon": w, "cliffs_delta": delta, "magnitude": magnitude,
        })
    holm = holm_correction([p["wilcoxon"]["p_value"] for p in pairwise])
    for p, h in zip(pairwise, holm):
        p["p_holm"] = h
    summary["pairwise"] = pairwise

    # --- kryteria: najlepsze wartości, udział rozwiązań bez ryzyka, rozmiar frontu, czas
    best = {a: {"makespan": [], "energy": [], "risk": [], "zero_risk_share": [], "zero_risk_pct": []} for a in ALGORITHMS}
    for run in main_data["runs"]:
        for a in ALGORITHMS:
            pts = np.array(main_data["points"][(run, a)])
            best[a]["makespan"].append(float(pts[:, 0].min()))
            best[a]["energy"].append(float(pts[:, 1].min()))
            best[a]["risk"].append(float(pts[:, 2].min()))
            best[a]["zero_risk_share"].append(float(np.mean(pts[:, 2] == 0.0)))
            best[a]["zero_risk_pct"].append(100.0 * float(np.mean(pts[:, 2] == 0.0)))
    summary["objectives"] = {
        a: {
            "best_makespan": describe(np.array(best[a]["makespan"])),
            "best_energy": describe(np.array(best[a]["energy"])),
            "best_risk": describe(np.array(best[a]["risk"])),
            "zero_risk_share": describe(np.array(best[a]["zero_risk_share"])),
            "front_size": describe(main_data["front_size"][a]),
            "time_ms": describe(main_data["time_ms"][a]),
            "all_arrived_share": float(np.mean(np.array(best[a]["makespan"]) <= 900.0)),
        }
        for a in ALGORITHMS
    }

    # --- zbieżność
    curves = fig_convergence(main_data, out)
    conv_by = defaultdict(dict)  # (alg, run) -> {iter: hv}
    for r in main_data["convergence"]:
        conv_by[(r["algorithm"], int(r["run"]))][int(r["iteration"])] = float(r["hypervolume_ratio"])
    iters_to_90 = {}
    for a in ALGORITHMS:
        vals = []
        for run in main_data["runs"]:
            series = conv_by[(a, run)]
            if not series:
                continue
            last_iter = max(series)
            target = 0.9 * series[last_iter]
            reached = [i for i in sorted(series) if series[i] >= target]
            vals.append(reached[0] if reached else last_iter)
        iters_to_90[a] = describe(np.array(vals, dtype=float)) if vals else None
    total_iters = max(max(c["iterations"]) for c in curves.values())
    fractions = [0.2, 0.4, 0.6, 0.8, 1.0]
    hv_at_fraction = {}
    for a in ALGORITHMS:
        it = np.array(curves[a]["iterations"])
        mean = np.array(curves[a]["mean"])
        hv_at_fraction[a] = {}
        for f in fractions:
            idx = int(np.argmin(np.abs(it - f * total_iters)))
            hv_at_fraction[a][str(f)] = float(mean[idx])
    summary["convergence"] = {"iters_to_90pct": iters_to_90, "hv_at_fraction": hv_at_fraction, "total_iterations": int(total_iters)}

    # --- budżet
    budgets = {}
    for spec in args.budget:
        label, prefix = spec.split("=", 1)
        budgets[int(label)] = load_prefix(prefix, max_runs=args.budget_runs)
    budget_hv = budget_hv_common(budgets) if budgets else {}
    budget_summary = {}
    for b in sorted(budgets):
        m = np.column_stack([budget_hv[b][a] for a in ALGORITHMS])
        fr = friedman_test(m)
        ranks_b = fr.pop("ranks")
        budget_summary[b] = {
            "n_runs": int(m.shape[0]),
            "hv": {a: describe(budget_hv[b][a]) for a in ALGORITHMS},
            "hv_per_budget_normalization": {a: describe(budgets[b]["hv"][a]) for a in ALGORITHMS},
            "mean_rank": dict(zip(ALGORITHMS, fr["mean_ranks"])),
            "wins": {a: int(np.sum(ranks_b[:, i] == 1.0)) for i, a in enumerate(ALGORITHMS)},
            "friedman_p": fr["p_value"],
            "time_ms": {a: float(np.mean(budgets[b]["time_ms"][a])) for a in ALGORITHMS},
        }
    summary["budget"] = budget_summary

    # --- rysunki
    fig_boxplot(main_data, out)
    if budgets:
        fig_budget(budget_hv, out, args.population)
    fig_fronts(main_data, args.front_run, out)
    fig_objectives(best, out)

    # --- tabele LaTeX
    write_tex(
        out / "tab_hv_summary.tex",
        ["Algorytm", "Średnia $\\pm$ odch. std.", "Mediana", "Min", "Max", "Średnia ranga", "Zwycięstwa"],
        [[a, f"{fmt(summary['hv'][a]['mean'])} $\\pm$ {fmt(summary['hv'][a]['sd'])}", fmt(summary["hv"][a]["median"]),
          fmt(summary["hv"][a]["min"]), fmt(summary["hv"][a]["max"]), fmt(summary["mean_rank"][a], 2),
          f"{wins[a]} / {n_runs}"] for a in ALGORITHMS],
        f"Znormalizowana hiperobjętość $\\mathrm{{HV}}/\\mathrm{{HV}}_{{\\max}}$ frontów końcowych ({n_runs} instancji)",
        "tab:hv_summary", "l c c c c c c",
    )
    write_tex(
        out / "tab_pairwise.tex",
        ["Para $A$ vs $B$", "Mediana $\\Delta$", "$W$", "$p$", "$p_{\\text{Holm}}$", "$\\delta$ Cliffa", "Efekt", "$A$ lepszy / $B$ lepszy"],
        [[f"{p['a']} vs {p['b']}", fmt(p["median_diff"]), fmt(p["wilcoxon"]["W"], 1), fmt_p(p["wilcoxon"]["p_value"]),
          fmt_p(p["p_holm"]), fmt(p["cliffs_delta"], 2), p["magnitude"], f"{p['wins_a']} / {p['wins_b']}"] for p in pairwise],
        "Testy Wilcoxona dla par (poprawka Holma) i wielkość efektu $\\delta$ Cliffa dla $\\mathrm{HV}/\\mathrm{HV}_{\\max}$",
        "tab:pairwise", "l c c c c c l c",
    )
    write_tex(
        out / "tab_objectives.tex",
        ["Algorytm", "Min. makespan", "Min. energia", "Wszystkie drony u celu", "Udział ryzyka $= 0$", "$|$front$|$", "Czas [s]"],
        [[a, fmt(summary["objectives"][a]["best_makespan"]["mean"], 1), fmt(summary["objectives"][a]["best_energy"]["mean"], 1),
          fmt(100 * summary["objectives"][a]["all_arrived_share"], 0) + "\\%", fmt(100 * summary["objectives"][a]["zero_risk_share"]["mean"], 1) + "\\%",
          fmt(summary["objectives"][a]["front_size"]["mean"], 1), fmt(summary["objectives"][a]["time_ms"]["mean"] / 1000.0, 2)]
         for a in ALGORITHMS],
        "Średnie (po instancjach) najlepsze wartości kryteriów na froncie, udział instancji z~wszystkimi dronami u~celu, udział rozwiązań o~zerowym ryzyku, rozmiar frontu i~czas obliczeń",
        "tab:objectives", "l c c c c c c",
    )
    write_tex(
        out / "tab_convergence.tex",
        ["Algorytm"] + [f"{int(f * 100)}\\% budżetu" for f in fractions] + ["Iteracje do 90\\% HV$_{\\text{końc.}}$ (mediana)"],
        [[a] + [fmt(hv_at_fraction[a][str(f)]) for f in fractions]
         + [fmt(iters_to_90[a]["median"], 0) if iters_to_90[a] else "---"] for a in ALGORITHMS],
        f"Średnia $\\mathrm{{HV}}/\\mathrm{{HV}}_{{\\max}}$ bieżącego zbioru niezdominowanego po ułamku budżetu ({total_iters} iteracji)",
        "tab:convergence", "l " + "c " * len(fractions) + "c",
    )
    if budget_summary:
        write_tex(
            out / "tab_budget.tex",
            ["Iteracje"] + ALGORITHMS + ["Zwycięstwa", "$p$ Friedmana"],
            [[str(b)] + [f"{fmt(budget_summary[b]['hv'][a]['mean'])} $\\pm$ {fmt(budget_summary[b]['hv'][a]['sd'])}" for a in ALGORITHMS]
             + [" / ".join(str(budget_summary[b]["wins"][a]) for a in ALGORITHMS), fmt_p(budget_summary[b]["friedman_p"])]
             for b in sorted(budget_summary)],
            f"Wrażliwość na budżet obliczeniowy: średnia $\\pm$ odch. std. $\\mathrm{{HV}}/\\mathrm{{HV}}_{{\\max}}$ przy normalizacji wspólnej dla instancji po wszystkich budżetach ({args.budget_runs} instancji na budżet)",
            "tab:budget", "l " + "c " * len(ALGORITHMS) + "c c",
        )
    # --- surowe wyniki per instancja (rozdział z wynikami)
    rows = []
    for run in main_data["runs"]:
        vals = [main_data["hv"][a][run] for a in ALGORITHMS]
        best_i = int(np.argmax(vals))
        row = [str(run + 1)]
        for i, a in enumerate(ALGORITHMS):
            hv_txt = ("\\textbf{" + fmt(vals[i]) + "}") if i == best_i else fmt(vals[i])
            row += [hv_txt, str(int(main_data["front_size"][a][run]))]
        rows.append(row)
    write_tex(out / "tab_per_run.tex",
              ["Instancja"] + [f"{a} HV" if k == 0 else f"{a} $|F|$" for a in ALGORITHMS for k in (0, 1)], rows,
              "Znormalizowana hiperobjętość $\\mathrm{HV}/\\mathrm{HV}_{\\max}$ (najlepszy wynik pogrubiony) i~rozmiar frontu końcowego $|F|$ w~poszczególnych instancjach",
              "tab:per_run", "l c c c c c c")
    rows = []
    for run in main_data["runs"]:
        row = [str(run + 1)]
        for a in ALGORITHMS:
            row += [fmt(best[a]["makespan"][run], 0), fmt(best[a]["energy"][run], 0)]
        rows.append(row)
    write_tex(out / "tab_per_run_objectives.tex",
              ["Instancja"] + [f"{a} $T_{{\\min}}$" if k == 0 else f"{a} $E_{{\\min}}$" for a in ALGORITHMS for k in (0, 1)], rows,
              "Najlepszy makespan $T_{\\min}$ [tiki] i~najlepsza energia $E_{\\min}$ na froncie końcowym w~poszczególnych instancjach (makespan $> 900$ oznacza, że żadna trasa nie kończy misji w~horyzoncie)",
              "tab:per_run_objectives", "l c c c c c c")
    if budget_hv:
        runs_b = sorted(set.intersection(*(set(d["runs"]) for d in budgets.values())))
        rows = []
        for b in sorted(budget_hv):
            for a in ALGORITHMS:
                rows.append([f"{b} / {a}"] + [fmt(v) for v in budget_hv[b][a]])
        write_tex(out / "tab_per_run_budget.tex",
                  ["Iteracje / algorytm"] + [str(r + 1) for r in runs_b], rows,
                  "Seria budżetowa: $\\mathrm{HV}/\\mathrm{HV}_{\\max}$ przy normalizacji wspólnej dla instancji po wszystkich budżetach; kolumny --- instancje 1--10",
                  "tab:per_run_budget", "l " + "c " * len(runs_b))

    (out / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")

    # --- konsola
    print(f"Instancje: {n_runs}")
    for a in ALGORITHMS:
        s = summary["hv"][a]
        print(f"  {a:9s} HV/max = {s['mean']:.3f} ± {s['sd']:.3f}  mediana {s['median']:.3f}  "
              f"ranga {summary['mean_rank'][a]:.2f}  zwycięstwa {wins[a]}/{n_runs}")
    print(f"Friedman: chi2 = {fried['statistic']:.2f}, p = {fried['p_value']:.2e}")
    for p in pairwise:
        print(f"  {p['a']} vs {p['b']}: mediana Δ = {p['median_diff']:+.3f}, W = {p['wilcoxon']['W']:.0f}, "
              f"p = {p['wilcoxon']['p_value']:.2e} (Holm {p['p_holm']:.2e}), δ = {p['cliffs_delta']:+.2f} ({p['magnitude']}), "
              f"{p['wins_a']}/{p['wins_b']} [{p['wilcoxon']['method']}]")
    for b in sorted(budget_summary):
        bs = budget_summary[b]
        print(f"  budżet {b:5d} (n={bs['n_runs']}): " + ", ".join(f"{a} {bs['hv'][a]['mean']:.3f}±{bs['hv'][a]['sd']:.3f} [r={bs['mean_rank'][a]:.2f}, w={bs['wins'][a]}]" for a in ALGORITHMS)
              + f"  p_F = {bs['friedman_p']:.2e}")
    print(f"Zapisano wyniki do {out}/")


if __name__ == "__main__":
    main()
