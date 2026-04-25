# Section 7: Supabase Backend & Leaderboard

## 7.1 Supabase Configuration

- **Plan:** Free tier
- **Database Size Budget:** 150 MB - oldest runs are pruned
- **Auth:** Not used. Uploads are unauthenticated inserts via Edge Function (service role).
- **Realtime:** Not used (polling leaderboard is sufficient).
- **Storage:** Not used.
- **Required secrets:** `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`

## 7.2 Tables

| Table | Description |
|---|---|
| `devices` | Device fingerprints (deduplicated by SHA256 of Build.FINGERPRINT) |
| `runs` | Per-run metadata (device, score, stability) |
| `results` | Per-benchmark per-run metrics (p50, p99, best, mean, throughput) |
| `category_scores` | Denormalized category scores per run |
| `benchmarks` | Static benchmark definitions (ID, category, unit, higher_is_better) |

## 7.3 Leaderboard RPC Functions

- `leaderboard_overall(p_app_version)` - Best run per device, ranked by overall score, filtered to stable runs in the last 90 days.
- `leaderboard_by_category(p_app_version)` - Best category score per device.

Both functions accept `p_app_version = NULL` to show all versions (with a UI warning about cross-version comparability).

## 7.4 Upload Flow

Uploads go through a Supabase Edge Function (`/functions/v1/upload-run`) that:
1. Upserts the device record
2. Rate-limits: max 10 uploads per device per hour
3. Inserts the run record
4. Batch-inserts all result rows
5. Inserts category score rows
6. **Enforces a 150 MB storage budget** - after a successful insert, the function checks the approximate size of the `runs` table (via `get_leaderboard_size_bytes` RPC or row-count estimation). If the total exceeds 150 MB, the oldest runs (by `created_at`) are deleted in batches of 50 until the size is back under budget. Foreign key CASCADE ensures child rows in `results` and `category_scores` are cleaned up automatically.
7. Returns `{ run_id }` on success

JWT verification is disabled for this function (`verify_jwt = false` in `config.toml`). The function uses the service role key internally to bypass RLS; clients only need the `apikey` header for gateway routing.

## 7.6 Local Persistence (Room Database)

All runs are stored locally in a Room database. Upload is optional. Three tables:
- `local_runs` - run metadata
- `local_results` - per-benchmark results
- `local_category_scores` - category score summaries

The `LocalRunEntity.isUploaded` flag tracks upload status.

## 7.7 Version Fairness

The app passes its own `app_version` to the leaderboard RPC by default. Users can toggle to "All versions" with a warning. Scores from different major versions are not directly comparable due to potential benchmark implementation changes.
