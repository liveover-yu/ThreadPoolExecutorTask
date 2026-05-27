import csv
from pathlib import Path

try:
    import matplotlib.pyplot as plt
except ModuleNotFoundError:
    plt = None


PROJECT_ROOT = Path(__file__).resolve().parents[1]
CSV_FILE = PROJECT_ROOT / "benchmark-results" / "cpu-sustained.csv"
OUTPUT_DIR = PROJECT_ROOT / "benchmark-results"
COLORS = {
    "custom": "#2563eb",
    "juc": "#f97316",
}


def read_rows():
    with CSV_FILE.open("r", encoding="utf-8", newline="") as file:
        return list(csv.DictReader(file))


def group_by_pool(rows):
    grouped = {}
    for row in rows:
        grouped.setdefault(row["poolType"], []).append(row)
    return grouped


def plot_metric(grouped, metric, title, ylabel, output_name):
    svg_name = output_name.replace(".png", ".svg")
    plot_metric_svg(grouped, metric, title, ylabel, svg_name)

    if plt is None:
        return

    plt.figure(figsize=(10, 5))

    for pool_type, rows in grouped.items():
        seconds = [int(row["second"]) for row in rows]
        values = [int(row[metric]) for row in rows]
        plt.plot(seconds, values, label=pool_type)

    plt.title(title)
    plt.xlabel("time (s)")
    plt.ylabel(ylabel)
    plt.legend()
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(OUTPUT_DIR / output_name, dpi=150)
    plt.close()


def plot_metric_svg(grouped, metric, title, ylabel, output_name):
    width = 1000
    height = 520
    left = 70
    right = 30
    top = 60
    bottom = 70
    chart_width = width - left - right
    chart_height = height - top - bottom

    all_seconds = []
    all_values = []
    for rows in grouped.values():
        all_seconds.extend(int(row["second"]) for row in rows)
        all_values.extend(int(row[metric]) for row in rows)

    max_second = max(all_seconds) if all_seconds else 1
    max_value = max(all_values) if all_values else 1
    max_value = max(max_value, 1)

    def x_pos(second):
        if max_second == 1:
            return left
        return left + (second - 1) * chart_width / (max_second - 1)

    def y_pos(value):
        return top + chart_height - value * chart_height / max_value

    lines = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="white"/>',
        f'<text x="{width / 2}" y="30" text-anchor="middle" font-size="22" font-family="Arial">{title}</text>',
        f'<line x1="{left}" y1="{top}" x2="{left}" y2="{top + chart_height}" stroke="#111827" stroke-width="1"/>',
        f'<line x1="{left}" y1="{top + chart_height}" x2="{left + chart_width}" y2="{top + chart_height}" stroke="#111827" stroke-width="1"/>',
        f'<text x="{width / 2}" y="{height - 20}" text-anchor="middle" font-size="14" font-family="Arial">time (s)</text>',
        f'<text x="18" y="{height / 2}" text-anchor="middle" transform="rotate(-90 18 {height / 2})" font-size="14" font-family="Arial">{ylabel}</text>',
        f'<text x="{left - 10}" y="{top + chart_height + 5}" text-anchor="end" font-size="12" font-family="Arial">0</text>',
        f'<text x="{left - 10}" y="{top + 5}" text-anchor="end" font-size="12" font-family="Arial">{max_value}</text>',
    ]

    legend_x = left + 10
    legend_y = top + 20

    for index, (pool_type, rows) in enumerate(grouped.items()):
        points = " ".join(
            f'{x_pos(int(row["second"])):.2f},{y_pos(int(row[metric])):.2f}'
            for row in rows
        )
        color = COLORS.get(pool_type, "#111827")
        lines.append(f'<polyline fill="none" stroke="{color}" stroke-width="2" points="{points}"/>')
        lines.append(f'<rect x="{legend_x}" y="{legend_y + index * 24 - 10}" width="14" height="14" fill="{color}"/>')
        lines.append(f'<text x="{legend_x + 22}" y="{legend_y + index * 24 + 2}" font-size="14" font-family="Arial">{pool_type}</text>')

    lines.append("</svg>")

    (OUTPUT_DIR / output_name).write_text("\n".join(lines), encoding="utf-8")


def main():
    if not CSV_FILE.exists():
        raise FileNotFoundError(f"CSV file not found: {CSV_FILE}")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    grouped = group_by_pool(read_rows())

    plot_metric(grouped, "throughput", "CPU sustained throughput", "finished tasks / second", "cpu-throughput.png")
    plot_metric(grouped, "finished", "CPU sustained finished tasks", "finished tasks", "cpu-finished.png")
    plot_metric(grouped, "activeCount", "CPU sustained active threads", "active threads", "cpu-active-count.png")
    plot_metric(grouped, "rejected", "CPU sustained rejected tasks", "rejected tasks", "cpu-rejected.png")

    if plt is None:
        print(f"matplotlib not found. SVG charts written to: {OUTPUT_DIR}")
    else:
        print(f"SVG and PNG charts written to: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
