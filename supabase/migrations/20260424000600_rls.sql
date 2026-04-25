---- Enable RLS

ALTER TABLE public.benchmarks      ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.runs            ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.results         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.category_scores ENABLE ROW LEVEL SECURITY;

---- Table privileges

REVOKE INSERT, UPDATE, DELETE ON public.benchmarks      FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.devices         FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.runs            FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.results         FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.category_scores FROM anon, authenticated;

GRANT SELECT ON public.benchmarks      TO anon, authenticated;
GRANT SELECT ON public.devices         TO anon, authenticated;
GRANT SELECT ON public.runs            TO anon, authenticated;
GRANT SELECT ON public.results         TO anon, authenticated;
GRANT SELECT ON public.category_scores TO anon, authenticated;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.benchmarks      TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.devices         TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.runs            TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.results         TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.category_scores TO service_role;

---- Public read-only leaderboard data

DROP POLICY IF EXISTS "anon_select_benchmarks" ON public.benchmarks;
DROP POLICY IF EXISTS "benchmarks_select_public" ON public.benchmarks;
CREATE POLICY "benchmarks_select_public"
  ON public.benchmarks FOR SELECT
  TO anon, authenticated
  USING (true);

DROP POLICY IF EXISTS "anon_select_devices" ON public.devices;
DROP POLICY IF EXISTS "devices_select_public" ON public.devices;
CREATE POLICY "devices_select_public"
  ON public.devices FOR SELECT
  TO anon, authenticated
  USING (true);

DROP POLICY IF EXISTS "anon_select_runs" ON public.runs;
DROP POLICY IF EXISTS "runs_select_public" ON public.runs;
CREATE POLICY "runs_select_public"
  ON public.runs FOR SELECT
  TO anon, authenticated
  USING (true);

DROP POLICY IF EXISTS "anon_select_results" ON public.results;
DROP POLICY IF EXISTS "results_select_public" ON public.results;
CREATE POLICY "results_select_public"
  ON public.results FOR SELECT
  TO anon, authenticated
  USING (true);

DROP POLICY IF EXISTS "anon_select_category_scores" ON public.category_scores;
DROP POLICY IF EXISTS "category_scores_select_public" ON public.category_scores;
CREATE POLICY "category_scores_select_public"
  ON public.category_scores FOR SELECT
  TO anon, authenticated
  USING (true);
