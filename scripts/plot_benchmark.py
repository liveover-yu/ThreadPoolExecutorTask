import csv
from pathlib import Path

try:
    import matplotlib.pyplot as plt
except ModuleNotFoundError:
    plt = None


PROJECT_ROOT = Path(__file__).resolve().parents[1]
CSV_FILE = PROJECT_ROOT / "benchmark-results" / "cpu-sustained.csv"
OUTPUT_DIR = PROJECT_ROOT / "benchmark-results"
MOVING_AVERAGE_WINDOW = 5
COLORS = {
    "custom": "#2563eb",
    "juc": "#f97316",
}


def read_rows():
    with CSV_FILE.open("r", encoding="utf-8", newline="") as file:
        rows = list(csv.DictReader(file))
    return [row for row in rows if row.get("phase", "measure") == "measure"]


def group_by_pool(rows):
    grouped = {}
    for row in rows:
        grouped.setdefault(row["poolType"], []).append(row)
    for items in grouped.values():
        items.sort(key=lambda row: (int(row.get("round", "1")), int(row["second"])))
    return grouped


def moving_average(values, window):
    result = []
    for index in range(len(values)):
        start = max(0, index - window + 1)
        part = values[start:index + 1]
        result.append(sum(part) / len(part))
    return result


def timeline(rows):
    if not rows:
        return []
    max_second = max(int(row["second"]) for row in rows)
    return [(int(row.get("round", "1")) - 1) * max_second + int(row["second"]) for row in rows]


def values_of(rows, metric):
    return [int(row[metric]) for row in rows]


def plot_metric(grouped, metric, title, ylabel, output_name, smooth=False):
    svg_name = output_name.replace(".png", ".svg")
    plot_metric_svg(grouped, metric, title, ylabel, svg_name, smooth)

    if plt is None:
        return

    plt.figure(figsize=(10, 5))

    for pool_type, rows in grouped.items():
        seconds = timeline(rows)
        values = values_of(rows, metric)
        if smooth:
            values = moving_average(values, MOVING_AVERAGE_WINDOW)
        plt.plot(seconds, values, label=pool_type)

    plt.title(title)
    plt.xlabel("measure time (s)")
    plt.ylabel(ylabel)
    plt.legend()
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(OUTPUT_DIR / output_name, dpi=150)
    plt.close()


def plot_metric_svg(grouped, metric, title, ylabel, output_name, smooth=False):
    width = 1000
    height = 520
    left = 80
    right = 30
    top = 60
    bottom = 70
    chart_width = width - left - right
    chart_height = height - top - bottom

    series = {}
    all_seconds = []
    all_values = []
    for pool_type, rows in grouped.items():
        seconds = timeline(rows)
        values = values_of(rows, metric)
        if smooth:
            values = moving_average(values, MOVING_AVERAGE_WINDOW)
        series[pool_type] = list(zip(seconds, values))
        all_seconds.extend(seconds)
        all_values.extend(values)

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
        f'<text x="{width / 2}" y="{height - 20}" text-anchor="middle" font-size="14" font-family="Arial">measure time (s)</text>',
        f'<text x="22" y="{height / 2}" text-anchor="middle" transform="rotate(-90 22 {height / 2})" font-size="14" font-family="Arial">{ylabel}</text>',
        f'<text x="{left - 10}" y="{top + chart_height + 5}" text-anchor="end" font-size="12" font-family="Arial">0</text>',
        f'<text x="{left - 10}" y="{top + 5}" text-anchor="end" font-size="12" font-family="Arial">{int(max_value)}</text>',
    ]

    legend_x = left + 10
    legend_y = top + 20

    for index, (pool_type, points_data) in enumerate(series.items()):
        points = " ".join(f"{x_pos(second):.2f},{y_pos(value):.2f}" for second, value in points_data)
        color = COLORS.get(pool_type, "#111827")
        lines.append(f'<polyline fill="none" stroke="{color}" stroke-width="2" points="{points}"/>')
        lines.append(f'<rect x="{legend_x}" y="{legend_y + index * 24 - 10}" width="14" height="14" fill="{color}"/>')
        lines.append(f'<text x="{legend_x + 22}" y="{legend_y + index * 24 + 2}" font-size="14" font-family="Arial">{pool_type}</text>')

    lines.append("</svg>")
    (OUTPUT_DIR / output_name).write_text("\n".join(lines), encoding="utf-8")


def write_summary(rows):
    grouped = group_by_pool(rows)
    summary_file = OUTPUT_DIR / "cpu-summary.md"
    lines = [
        "| poolType | samples | totalFinished | avgThroughput | minThroughput | maxThroughput | totalRejected |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]

    for pool_type, items in grouped.items():
        throughputs = values_of(items, "throughput")
        finished = values_of(items, "finished")
        rejected = values_of(items, "rejected")
        lines.append(
            f"| {pool_type} | {len(items)} | {sum(throughputs)} | "
            f"{sum(throughputs) / len(throughputs):.2f} | {min(throughputs)} | {max(throughputs)} | {max(rejected)} |"
        )

    summary_file.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    if not CSV_FILE.exists():
        raise FileNotFoundError(f"CSV file not found: {CSV_FILE}")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    rows = read_rows()
    grouped = group_by_pool(rows)

    plot_metric(grouped, "throughput", "CPU throughput per second", "finished tasks / second", "cpu-throughput.png")
    plot_metric(
        grouped,
        "throughput",
        f"CPU throughput moving average ({MOVING_AVERAGE_WINDOW}s)",
        "finished tasks / second",
        "cpu-throughput-ma.png",
        smooth=True,
    )
    plot_metric(grouped, "finished", "CPU finished tasks by round", "finished tasks", "cpu-finished.png")
    plot_metric(grouped, "activeCount", "CPU active threads", "active threads", "cpu-active-count.png")
    plot_metric(grouped, "rejected", "CPU rejected tasks", "rejected tasks", "cpu-rejected.png")
    write_summary(rows)

    if plt is None:
        print(f"matplotlib not found. SVG charts written to: {OUTPUT_DIR}")
    else:
        print(f"SVG and PNG charts written to: {OUTPUT_DIR}")
    print(f"Summary written to: {OUTPUT_DIR / 'cpu-summary.md'}")


if __name__ == "__main__":
    main()
