---- Catalog tables

CREATE TABLE IF NOT EXISTS public.benchmarks (
  id               text PRIMARY KEY,
  category         text NOT NULL,
  display_name     text NOT NULL DEFAULT '',
  unit             text NOT NULL,
  higher_is_better boolean NOT NULL DEFAULT true,
  created_at       timestamptz NOT NULL DEFAULT now()
);

---- Runtime tables

CREATE TABLE IF NOT EXISTS public.devices (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  brand            text NOT NULL,
  model            text NOT NULL,
  device_name      text NOT NULL DEFAULT '',
  soc              text NOT NULL,
  abi              text NOT NULL,
  cpu_cores        integer NOT NULL,
  ram_bytes        bigint NOT NULL,
  android_api      integer NOT NULL,
  fingerprint_hash text NOT NULL UNIQUE,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.runs (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id        uuid NOT NULL REFERENCES public.devices(id),
  display_name     text NOT NULL DEFAULT 'Anonymous',
  app_version      text NOT NULL,
  overall_score    double precision,
  battery_level    integer NOT NULL DEFAULT 0,
  is_charging      boolean NOT NULL DEFAULT false,
  stability_rating text,
  started_at       timestamptz NOT NULL DEFAULT now(),
  completed_at     timestamptz NOT NULL DEFAULT now(),
  created_at       timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.results (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id       uuid NOT NULL REFERENCES public.runs(id) ON DELETE CASCADE,
  benchmark_id text NOT NULL REFERENCES public.benchmarks(id),
  display_name text NOT NULL DEFAULT '',
  metric_p50   double precision,
  metric_p99   double precision,
  metric_best  double precision,
  metric_mean  double precision,
  throughput   double precision,
  score        double precision,
  variance_pct double precision
);

CREATE TABLE IF NOT EXISTS public.category_scores (
  id       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id   uuid NOT NULL REFERENCES public.runs(id) ON DELETE CASCADE,
  category text NOT NULL,
  score    double precision
);

---- Column additions

ALTER TABLE public.benchmarks ADD COLUMN IF NOT EXISTS display_name text;
ALTER TABLE public.benchmarks ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE public.devices    ADD COLUMN IF NOT EXISTS updated_at timestamptz;

UPDATE public.benchmarks
SET display_name = initcap(replace(id, '.', ' '))
WHERE display_name IS NULL;

UPDATE public.benchmarks
SET created_at = now()
WHERE created_at IS NULL;

UPDATE public.devices
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE public.benchmarks ALTER COLUMN display_name SET DEFAULT '';
ALTER TABLE public.benchmarks ALTER COLUMN display_name SET NOT NULL;
ALTER TABLE public.benchmarks ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE public.benchmarks ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE public.devices    ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE public.devices    ALTER COLUMN updated_at SET NOT NULL;

---- Indexes

CREATE INDEX IF NOT EXISTS idx_benchmarks_category ON public.benchmarks (category);

CREATE INDEX IF NOT EXISTS idx_runs_device_id ON public.runs (device_id);
CREATE INDEX IF NOT EXISTS idx_runs_created_at ON public.runs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_runs_leaderboard
  ON public.runs (app_version, created_at DESC, overall_score DESC NULLS LAST)
  WHERE stability_rating IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_results_run_id ON public.results (run_id);
CREATE INDEX IF NOT EXISTS idx_results_benchmark_id ON public.results (benchmark_id);

CREATE INDEX IF NOT EXISTS idx_category_scores_run_id ON public.category_scores (run_id);
CREATE INDEX IF NOT EXISTS idx_category_scores_category_score
  ON public.category_scores (category, score DESC NULLS LAST);
