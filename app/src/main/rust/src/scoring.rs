//! Population-normalized scoring.
//!
//! Score = 1000 × (device_value / population_median)
//! Where device_value and population_median are both expressed in
//! "higher-is-better" form (ops/sec or MB/s).
//!
//! During the bootstrap phase (< 50 submissions per season), the server
//! returns NULL for scores. Scoring here is only used when Supabase
//! provides a median (i.e., after bootstrap completes).
//!
//! This module provides the math; the actual median comes from the server.


/// Compute a population-normalized score given a device metric and the
/// population median *in the same units* and with the same direction (i.e.,
/// both have already been converted to "higher is better" form).
///
/// Returns the score clamped to [0, 5000]. A 1× device scores 1000;
/// a 1.5× device scores 1500; a 0.67× device scores 670.
///
/// # Arguments
/// * `device_value`  - the device's p50 metric value (higher is better).
/// * `pop_median`    - the population median (same units).
///
/// # Panics
/// Never panics; guards division-by-zero with a zero-score return.
pub fn population_score(device_value: f64, pop_median: f64) -> f64 {
    if pop_median <= 0.0 {
        return 0.0;
    }
    (1000.0 * (device_value / pop_median)).clamp(0.0, 5000.0)
}

/// Overall score = weighted geometric mean of per-category scores
/// with equal weights (1/N for N categories).
///
/// Returns None if `category_scores` is empty or any score is non-positive.
pub fn overall_score(category_scores: &[f64]) -> Option<f64> {
    if category_scores.is_empty() {
        return None;
    }
    if category_scores.iter().any(|&s| s <= 0.0) {
        return None;
    }
    let log_sum: f64 = category_scores.iter().map(|s| s.ln()).sum();
    let geo_mean = (log_sum / category_scores.len() as f64).exp();
    Some(geo_mean)
}

/// Converts a latency metric (lower-is-better, e.g. ns/op) to throughput space
/// (higher-is-better) for population-normalized scoring.
///
/// Returns `1e9 / latency_ns` (ops per second from ns/op).
/// Clamps at `f64::MAX` if `latency_ns` is zero.
pub fn latency_to_throughput(latency_ns: f64) -> f64 {
    if latency_ns <= 0.0 {
        return f64::MAX;
    }
    1_000_000_000.0 / latency_ns
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn score_at_median_is_1000() {
        let s = population_score(50.0, 50.0);
        assert!((s - 1000.0).abs() < 0.001, "score at median should be 1000, got {s}");
    }

    #[test]
    fn score_above_median() {
        let s = population_score(75.0, 50.0);
        assert!((s - 1500.0).abs() < 0.001, "1.5× should score 1500, got {s}");
    }

    #[test]
    fn score_below_median() {
        let s = population_score(25.0, 50.0);
        assert!((s - 500.0).abs() < 0.001, "0.5× should score 500, got {s}");
    }

    #[test]
    fn zero_median_returns_zero() {
        assert_eq!(population_score(100.0, 0.0), 0.0);
    }

    #[test]
    fn overall_geometric_mean() {
        let scores = vec![1000.0, 2000.0, 500.0];
        let g = overall_score(&scores).unwrap();
        // Geometric mean(1000, 2000, 500) = (1000×2000×500)^(1/3) ≈ 1000
        let expected = (1000.0_f64 * 2000.0 * 500.0_f64).powf(1.0 / 3.0);
        assert!((g - expected).abs() < 1.0, "geometric mean mismatch: {g} vs {expected}");
    }
}
