---- Functions

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.get_leaderboard_size_bytes()
RETURNS bigint
LANGUAGE sql
STABLE
SET search_path = ''
AS $$
  SELECT COALESCE(
    pg_total_relation_size('public.runs') +
    pg_total_relation_size('public.results') +
    pg_total_relation_size('public.category_scores') +
    pg_total_relation_size('public.devices') +
    pg_total_relation_size('public.benchmarks'),
    0
  );
$$;

CREATE OR REPLACE FUNCTION public.estimated_category_score(
  p_run_id uuid,
  p_category text
)
RETURNS double precision
LANGUAGE sql
STABLE
SET search_path = ''
AS $$
  SELECT CASE
    WHEN COUNT(*) FILTER (
      WHERE res.throughput IS NOT NULL
        AND res.throughput > 0
    ) = 0 THEN NULL
    ELSE LEAST(
      5000.0,
      GREATEST(
        0.0,
        (
          AVG(LN(res.throughput)) FILTER (
            WHERE res.throughput IS NOT NULL
              AND res.throughput > 0
          ) / LN(10.0)
        ) * 200.0
      )
    )
  END
  FROM public.results res
  JOIN public.benchmarks b ON b.id = res.benchmark_id
  WHERE res.run_id = p_run_id
    AND b.category = p_category;
$$;

CREATE OR REPLACE FUNCTION public.insert_benchmark_run(p_payload jsonb)
RETURNS uuid
LANGUAGE plpgsql
SET search_path = ''
AS $$
DECLARE
  v_device_id uuid;
  v_run_id uuid;
  v_result jsonb;
  v_cat jsonb;
BEGIN
  IF p_payload IS NULL OR jsonb_typeof(p_payload) <> 'object' THEN
    RAISE EXCEPTION 'insert_benchmark_run requires a JSON object payload';
  END IF;

  IF jsonb_typeof(p_payload->'device') <> 'object' THEN
    RAISE EXCEPTION 'insert_benchmark_run requires payload.device';
  END IF;

  IF jsonb_typeof(p_payload->'run') <> 'object' THEN
    RAISE EXCEPTION 'insert_benchmark_run requires payload.run';
  END IF;

  IF jsonb_typeof(p_payload->'results') <> 'array' THEN
    RAISE EXCEPTION 'insert_benchmark_run requires payload.results array';
  END IF;

  IF jsonb_typeof(p_payload->'category_scores') <> 'array' THEN
    RAISE EXCEPTION 'insert_benchmark_run requires payload.category_scores array';
  END IF;

  INSERT INTO public.devices (
    brand,
    model,
    device_name,
    soc,
    abi,
    cpu_cores,
    ram_bytes,
    android_api,
    fingerprint_hash
  )
  VALUES (
    p_payload->'device'->>'brand',
    p_payload->'device'->>'model',
    COALESCE(p_payload->'device'->>'device_name', ''),
    p_payload->'device'->>'soc',
    p_payload->'device'->>'abi',
    (p_payload->'device'->>'cpu_cores')::integer,
    (p_payload->'device'->>'ram_bytes')::bigint,
    (p_payload->'device'->>'android_api')::integer,
    p_payload->'device'->>'fingerprint_hash'
  )
  ON CONFLICT (fingerprint_hash) DO UPDATE
    SET brand = EXCLUDED.brand,
        model = EXCLUDED.model,
        device_name = EXCLUDED.device_name,
        soc = EXCLUDED.soc,
        abi = EXCLUDED.abi,
        cpu_cores = EXCLUDED.cpu_cores,
        ram_bytes = EXCLUDED.ram_bytes,
        android_api = EXCLUDED.android_api
  RETURNING id INTO v_device_id;

  INSERT INTO public.runs (
    device_id,
    display_name,
    app_version,
    overall_score,
    battery_level,
    is_charging,
    stability_rating,
    started_at,
    completed_at
  )
  VALUES (
    v_device_id,
    COALESCE(NULLIF(p_payload->'run'->>'display_name', ''), 'Anonymous'),
    p_payload->'run'->>'app_version',
    (p_payload->'run'->>'overall_score')::double precision,
    COALESCE((p_payload->'run'->>'battery_level')::integer, 0),
    COALESCE((p_payload->'run'->>'is_charging')::boolean, false),
    p_payload->'run'->>'stability_rating',
    COALESCE((p_payload->'run'->>'started_at')::timestamptz, now()),
    COALESCE((p_payload->'run'->>'completed_at')::timestamptz, now())
  )
  RETURNING id INTO v_run_id;

  FOR v_result IN SELECT * FROM jsonb_array_elements(p_payload->'results')
  LOOP
    INSERT INTO public.benchmarks (
      id,
      category,
      display_name,
      unit,
      higher_is_better
    )
    VALUES (
      v_result->>'benchmark_id',
      COALESCE(v_result->>'category', 'unknown'),
      COALESCE(v_result->>'display_name', ''),
      COALESCE(v_result->>'unit', 'unknown'),
      COALESCE((v_result->>'higher_is_better')::boolean, true)
    )
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.results (
      run_id,
      benchmark_id,
      display_name,
      metric_p50,
      metric_p99,
      metric_best,
      metric_mean,
      throughput,
      score,
      variance_pct
    )
    VALUES (
      v_run_id,
      v_result->>'benchmark_id',
      COALESCE(v_result->>'display_name', ''),
      (v_result->>'metric_p50')::double precision,
      (v_result->>'metric_p99')::double precision,
      (v_result->>'metric_best')::double precision,
      (v_result->>'metric_mean')::double precision,
      (v_result->>'throughput')::double precision,
      (v_result->>'score')::double precision,
      (v_result->>'variance_pct')::double precision
    );
  END LOOP;

  FOR v_cat IN SELECT * FROM jsonb_array_elements(p_payload->'category_scores')
  LOOP
    INSERT INTO public.category_scores (run_id, category, score)
    VALUES (
      v_run_id,
      v_cat->>'category',
      COALESCE(
        (v_cat->>'score')::double precision,
        public.estimated_category_score(v_run_id, v_cat->>'category')
      )
    );
  END LOOP;

  RETURN v_run_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.leaderboard_overall(
  p_app_version text DEFAULT NULL,
  p_fingerprint_hash text DEFAULT NULL,
  p_offset integer DEFAULT 0,
  p_limit integer DEFAULT 50
)
RETURNS TABLE (
  run_id uuid,
  display_name text,
  app_version text,
  brand text,
  model text,
  soc text,
  cpu_cores integer,
  android_api integer,
  overall_score double precision,
  battery_level integer,
  is_charging boolean,
  stability_rating text,
  abi text,
  ram_bytes bigint,
  started_at timestamptz,
  completed_at timestamptz
)
LANGUAGE sql
STABLE
SET search_path = ''
AS $$
  SELECT
    r.id AS run_id,
    r.display_name,
    r.app_version,
    d.brand,
    d.model,
    d.soc,
    d.cpu_cores,
    d.android_api,
    r.overall_score,
    r.battery_level,
    r.is_charging,
    r.stability_rating,
    d.abi,
    d.ram_bytes,
    r.started_at,
    r.completed_at
  FROM public.runs r
  JOIN public.devices d ON d.id = r.device_id
  WHERE r.created_at >= now() - INTERVAL '90 days'
    AND r.stability_rating IS NOT NULL
    AND (p_app_version IS NULL OR r.app_version = p_app_version)
    AND (p_fingerprint_hash IS NULL OR d.fingerprint_hash = p_fingerprint_hash)
  ORDER BY r.overall_score DESC NULLS LAST
  OFFSET GREATEST(p_offset, 0)
  LIMIT LEAST(GREATEST(p_limit, 1), 100);
$$;

CREATE OR REPLACE FUNCTION public.leaderboard_app_versions()
RETURNS TABLE (app_version text)
LANGUAGE sql
STABLE
SET search_path = ''
AS $$
  SELECT DISTINCT r.app_version
  FROM public.runs r
  WHERE r.created_at >= now() - INTERVAL '90 days'
    AND r.stability_rating IS NOT NULL
  ORDER BY r.app_version DESC;
$$;

CREATE OR REPLACE FUNCTION public.leaderboard_by_category(
  p_app_version text DEFAULT NULL,
  p_offset integer DEFAULT 0,
  p_limit integer DEFAULT 50
)
RETURNS TABLE (
  run_id uuid,
  display_name text,
  app_version text,
  brand text,
  model text,
  soc text,
  cpu_cores integer,
  android_api integer,
  category text,
  score double precision,
  stability_rating text,
  abi text,
  ram_bytes bigint,
  completed_at timestamptz
)
LANGUAGE sql
STABLE
SET search_path = ''
AS $$
  SELECT
    r.id AS run_id,
    r.display_name,
    r.app_version,
    d.brand,
    d.model,
    d.soc,
    d.cpu_cores,
    d.android_api,
    cs.category,
    cs.score,
    r.stability_rating,
    d.abi,
    d.ram_bytes,
    r.completed_at
  FROM public.runs r
  JOIN public.devices d ON d.id = r.device_id
  JOIN public.category_scores cs ON cs.run_id = r.id
  WHERE r.created_at >= now() - INTERVAL '90 days'
    AND r.stability_rating IS NOT NULL
    AND (p_app_version IS NULL OR r.app_version = p_app_version)
  ORDER BY cs.score DESC NULLS LAST
  OFFSET GREATEST(p_offset, 0)
  LIMIT LEAST(GREATEST(p_limit, 1), 100);
$$;

CREATE OR REPLACE FUNCTION public.run_detail(p_run_id uuid)
RETURNS TABLE (
  run_id uuid,
  display_name text,
  app_version text,
  brand text,
  model text,
  soc text,
  cpu_cores integer,
  android_api integer,
  overall_score double precision,
  battery_level integer,
  is_charging boolean,
  stability_rating text,
  abi text,
  ram_bytes bigint,
  started_at timestamptz,
  completed_at timestamptz
)
LANGUAGE sql
STABLE
SET search_path = ''
AS $$
  SELECT
    r.id AS run_id,
    r.display_name,
    r.app_version,
    d.brand,
    d.model,
    d.soc,
    d.cpu_cores,
    d.android_api,
    r.overall_score,
    r.battery_level,
    r.is_charging,
    r.stability_rating,
    d.abi,
    d.ram_bytes,
    r.started_at,
    r.completed_at
  FROM public.runs r
  JOIN public.devices d ON d.id = r.device_id
  WHERE r.id = p_run_id;
$$;

CREATE OR REPLACE FUNCTION public.run_category_benchmarks(p_run_id uuid)
RETURNS TABLE (
  category text,
  benchmark_id text,
  display_name text,
  unit text,
  metric_p50 double precision,
  metric_p99 double precision,
  metric_best double precision,
  metric_mean double precision,
  throughput double precision,
  variance_pct double precision,
  score double precision
)
LANGUAGE sql
STABLE
SET search_path = ''
AS $$
  SELECT
    b.category,
    res.benchmark_id,
    COALESCE(
      NULLIF(res.display_name, ''),
      NULLIF(b.display_name, ''),
      initcap(replace(res.benchmark_id, '_', ' '))
    ) AS display_name,
    b.unit,
    res.metric_p50,
    res.metric_p99,
    res.metric_best,
    res.metric_mean,
    res.throughput,
    res.variance_pct,
    res.score
  FROM public.results res
  JOIN public.benchmarks b ON b.id = res.benchmark_id
  WHERE res.run_id = p_run_id
  ORDER BY
    CASE b.category
      WHEN 'cpu' THEN 1
      WHEN 'memory' THEN 2
      WHEN 'scheduler' THEN 3
      WHEN 'ipc' THEN 4
      WHEN 'io' THEN 5
      WHEN 'network' THEN 6
      WHEN 'timer' THEN 7
      ELSE 8
    END,
    display_name,
    res.benchmark_id;
$$;

---- Triggers

DROP TRIGGER IF EXISTS set_updated_at ON public.devices;
CREATE TRIGGER set_updated_at
  BEFORE UPDATE ON public.devices
  FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

---- Function privileges

REVOKE ALL ON FUNCTION public.set_updated_at() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_leaderboard_size_bytes() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.estimated_category_score(uuid, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.insert_benchmark_run(jsonb) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.leaderboard_overall(text, text, integer, integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.leaderboard_app_versions() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.leaderboard_by_category(text, integer, integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.run_detail(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.run_category_benchmarks(uuid) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.get_leaderboard_size_bytes() TO service_role;
GRANT EXECUTE ON FUNCTION public.estimated_category_score(uuid, text) TO service_role;
GRANT EXECUTE ON FUNCTION public.insert_benchmark_run(jsonb) TO service_role;

GRANT EXECUTE ON FUNCTION public.leaderboard_overall(text, text, integer, integer) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.leaderboard_app_versions() TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.leaderboard_by_category(text, integer, integer) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.run_detail(uuid) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.run_category_benchmarks(uuid) TO anon, authenticated;
