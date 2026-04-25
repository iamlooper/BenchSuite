# Section 5: Scoring System

## 5.1 Design: Population-Normalized Scoring

BenchSuite does **not** use a fixed reference device or hardcoded baseline values. Instead, scores are computed relative to the **global population of all submitted benchmark runs**.

## 5.2 How It Works

### Step 1: Raw Metric Normalization

All metrics are first converted to a "higher is better" orientation:
- **Throughput metrics** (MB/s, ops/sec): used as-is.
- **Latency metrics** (ns/op, µs/roundtrip): inverted to ops/sec. `score_input = 1 / raw_latency`.

### Step 2: Per-Benchmark Scoring (Population Ratio)

```
median_b = median of all raw results (higher-is-better oriented)
ratio_b  = measured_value_b / median_b
score_b  = 1000 × ratio_b
```

- **Score 1000** = exactly at the population median.
- **Score 2000** = twice as fast as typical.
- **Score 500** = half as fast as typical.
- Scores clamped to `[100, 10000]`.

### Step 3: Population Bootstrap

When the leaderboard has fewer than 50 submissions:
- Scores are displayed as **raw metrics only** (no normalized score).
- The UI shows a "Leaderboard calibrating" indicator.
- Once ≥50 submissions exist per benchmark, normalized scores activate retroactively.

## 5.3 Category Score

```
category_score = geometric_mean(score_b for b in category)
```

Geometric mean prevents a single outlier benchmark from dominating.

## 5.4 Overall Score

Weighted geometric mean of category scores:

| Category | Weight |
|---|---|
| CPU & Syscall | 20% |
| Memory | 20% |
| Scheduler | 15% |
| IPC | 15% |
| Storage I/O | 15% |
| Network | 10% |
| Timers | 5% |

## 5.5 Score Display

- **Overall Score:** Single large number displayed prominently. 1000 = population median.
- **Category Scores:** Shown in a radar/spider chart with 7 axes.
- **Per-Benchmark Scores:** Expandable detail view with raw metric, normalized score, and p50/p99.
- **Stability Rating:** Based on the **median IQR-based dispersion** (IQR / median × 100) across all benchmarks in the run. IQR-based dispersion is used instead of classic CV% because system benchmarks produce heavy-tailed distributions where rare outliers (scheduler preemptions, cache misses) inflate standard deviation by orders of magnitude. The IQR considers only the middle 50% of samples, giving a robust measure of run-to-run consistency. Using the median (rather than the mean) across benchmarks prevents a few high-variance benchmarks from dominating the rating:
  - **Excellent:** median dispersion < 8%
  - **Good:** median dispersion 8–20%
  - **Fair:** median dispersion 20–35%
  - **Unstable:** median dispersion > 35%

## 5.6 Score Tier Colors

| Score Range | Color | Meaning |
|---|---|---|
| < 500 | Red | Well below median |
| 500–999 | Amber | Below median |
| 1000–1499 | Green | Above median |
| ≥ 1500 | Blue | Exceptional |
