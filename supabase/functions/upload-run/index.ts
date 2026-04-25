import "@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from "@supabase/supabase-js"

/**
 * upload-run Edge Function
 *
 * Accepts a JSON body matching the `upload_run_payload` shape and inserts
 * one row into `runs` plus N rows into `results` within a transaction
 * executed via the `insert_benchmark_run` RPC (defined in the DB migration).
 *
 * The function validates required fields and returns a JSON body with
 * { run_id } on success or { error } on failure.
 *
 * After a successful insert, the function checks the approximate size of the
 * `runs` table. If it exceeds 150 MB (Supabase free-tier budget), the oldest
 * rows (by `created_at`) are deleted in a loop until the size drops below the
 * threshold.
 *
 * Authentication: JWT verification is disabled for this function
 * (verify_jwt = false in config.toml). The function uses the service role
 * key internally to bypass RLS. Clients only need the `apikey` header for
 * Supabase gateway routing.
 */

const MAX_TABLE_BYTES = 150 * 1024 * 1024 // 150 MB

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "apikey, authorization, content-type",
      },
    })
  }

  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405)
  }

  let body: unknown
  try {
    body = await req.json()
  } catch {
    return json({ error: "Invalid JSON body" }, 400)
  }

  const payload = body as Record<string, unknown>

  // Validate top-level structure sent by the Kotlin client.
  const device = payload.device
  const run = payload.run
  const results = payload.results
  const categoryScores = payload.category_scores

  if (!isRecord(device)) {
    return json({ error: "Missing required field: device" }, 400)
  }
  if (!isRecord(run)) {
    return json({ error: "Missing required field: run" }, 400)
  }
  if (!Array.isArray(results)) {
    return json({ error: "Missing required field: results" }, 400)
  }
  if (!Array.isArray(categoryScores)) {
    return json({ error: "Missing required field: category_scores" }, 400)
  }

  const requiredDevice = ["brand", "model", "soc", "abi", "cpu_cores", "ram_bytes", "android_api", "fingerprint_hash"]
  for (const field of requiredDevice) {
    if (device[field] === undefined || device[field] === null) {
      return json({ error: `Missing required field: device.${field}` }, 400)
    }
  }

  const requiredDeviceStrings = ["brand", "model", "soc", "abi", "fingerprint_hash"]
  for (const field of requiredDeviceStrings) {
    const value = device[field]
    if (typeof value !== "string" || value.trim() === "") {
      return json({ error: `Invalid field: device.${field} must be a non-empty string` }, 400)
    }
  }

  const requiredDeviceNumbers = ["cpu_cores", "ram_bytes", "android_api"]
  for (const field of requiredDeviceNumbers) {
    const value = device[field]
    if (typeof value !== "number" || !Number.isFinite(value)) {
      return json({ error: `Invalid field: device.${field} must be a finite number` }, 400)
    }
  }

  const requiredRun = ["app_version"]
  for (const field of requiredRun) {
    if (run[field] === undefined || run[field] === null) {
      return json({ error: `Missing required field: run.${field}` }, 400)
    }
  }

  if (typeof run.app_version !== "string" || run.app_version.trim() === "") {
    return json({ error: "Invalid field: run.app_version must be a non-empty string" }, 400)
  }
  if (run.battery_level !== undefined && run.battery_level !== null) {
    if (typeof run.battery_level !== "number" || !Number.isFinite(run.battery_level)) {
      return json({ error: "Invalid field: run.battery_level must be a finite number" }, 400)
    }
  }
  if (run.overall_score !== undefined && run.overall_score !== null) {
    if (typeof run.overall_score !== "number" || !Number.isFinite(run.overall_score)) {
      return json({ error: "Invalid field: run.overall_score must be a finite number" }, 400)
    }
  }
  if (run.display_name !== undefined && run.display_name !== null && typeof run.display_name !== "string") {
    return json({ error: "Invalid field: run.display_name must be a string" }, 400)
  }
  if (run.started_at !== undefined && run.started_at !== null && typeof run.started_at !== "string") {
    return json({ error: "Invalid field: run.started_at must be an ISO-8601 string" }, 400)
  }
  if (run.completed_at !== undefined && run.completed_at !== null && typeof run.completed_at !== "string") {
    return json({ error: "Invalid field: run.completed_at must be an ISO-8601 string" }, 400)
  }
  if (run.is_charging !== undefined && run.is_charging !== null && typeof run.is_charging !== "boolean") {
    return json({ error: "Invalid field: run.is_charging must be a boolean" }, 400)
  }

  for (let i = 0; i < results.length; i++) {
    const result = results[i]
    if (!isRecord(result)) {
      return json({ error: `Invalid field: results[${i}] must be an object` }, 400)
    }
    if (typeof result.benchmark_id !== "string" || result.benchmark_id.trim() === "") {
      return json({ error: `Invalid field: results[${i}].benchmark_id must be a non-empty string` }, 400)
    }
    const optionalResultStrings = ["display_name", "category", "unit"]
    for (const field of optionalResultStrings) {
      if (result[field] !== undefined && result[field] !== null && typeof result[field] !== "string") {
        return json({ error: `Invalid field: results[${i}].${field} must be a string` }, 400)
      }
    }
    const optionalResultNumbers = [
      "metric_p50",
      "metric_p99",
      "metric_best",
      "metric_mean",
      "throughput",
      "score",
      "variance_pct",
    ]
    for (const field of optionalResultNumbers) {
      const value = result[field]
      if (value !== undefined && value !== null && (typeof value !== "number" || !Number.isFinite(value))) {
        return json({ error: `Invalid field: results[${i}].${field} must be a finite number` }, 400)
      }
    }
    if (result.higher_is_better !== undefined && result.higher_is_better !== null && typeof result.higher_is_better !== "boolean") {
      return json({ error: `Invalid field: results[${i}].higher_is_better must be a boolean` }, 400)
    }
  }

  for (let i = 0; i < categoryScores.length; i++) {
    const categoryScore = categoryScores[i]
    if (!isRecord(categoryScore)) {
      return json({ error: `Invalid field: category_scores[${i}] must be an object` }, 400)
    }
    if (typeof categoryScore.category !== "string" || categoryScore.category.trim() === "") {
      return json({ error: `Invalid field: category_scores[${i}].category must be a non-empty string` }, 400)
    }
    if (categoryScore.score !== undefined && categoryScore.score !== null) {
      if (typeof categoryScore.score !== "number" || !Number.isFinite(categoryScore.score)) {
        return json({ error: `Invalid field: category_scores[${i}].score must be a finite number` }, 400)
      }
    }
  }

  const fingerprintHash = device.fingerprint_hash as string

  try {
    const supabase = createServiceClient()

    const { data, error } = await supabase.rpc("insert_benchmark_run", {
      p_payload: payload,
    })

    if (error) {
      console.error("insert_benchmark_run failed:", JSON.stringify(error))
      return json({ error: `insert_benchmark_run failed: ${error.message}` }, 500)
    }

    // Enforce 150 MB size budget on the runs table.
    // Uses pg_total_relation_size which includes indexes and TOAST data.
    await enforceStorageLimit(supabase)

    // Enforce per-device run limit (keep 10 most recent per device).
    // Prevents a single device from accumulating unbounded history.
    await enforcePerDeviceRunLimit(supabase, fingerprintHash)

    return json({ run_id: data }, 201)
  } catch (err) {
    console.error("Unhandled error in upload-run:", err)
    return json({ error: "Internal server error" }, 500)
  }
})

/**
 * Checks the approximate total size of the `runs` table (including indexes,
 * TOAST, and related `results` / `category_scores` tables via CASCADE).
 * If the combined size exceeds MAX_TABLE_BYTES, deletes the oldest runs
 * (by `created_at`) in batches until the size is back under budget.
 */
async function enforceStorageLimit(
  supabase: ReturnType<typeof createClient>,
): Promise<void> {
  try {
    // Query total size of leaderboard-related tables
    const { data: sizeData, error: sizeError } = await supabase.rpc(
      "get_leaderboard_size_bytes",
    )

    // If the RPC doesn't exist, fall back to a direct SQL estimate via runs row count
    let totalBytes: number
    if (sizeError || sizeData === null || sizeData === undefined) {
      // Fallback: estimate from row count (approximate ~2 KB per run with results)
      const { count, error: countError } = await supabase
        .from("runs")
        .select("*", { count: "exact", head: true })
      if (countError || count === null) {
        console.warn("enforceStorageLimit: could not estimate table size, skipping")
        return
      }
      totalBytes = count * 2048
    } else {
      totalBytes = Number(sizeData)
    }

    if (totalBytes <= MAX_TABLE_BYTES) return

    console.log(
      `Storage limit exceeded: ${(totalBytes / 1024 / 1024).toFixed(1)} MB > ` +
      `${(MAX_TABLE_BYTES / 1024 / 1024).toFixed(0)} MB. Trimming oldest runs.`,
    )

    // Delete oldest runs in batches of 50 until under budget.
    // Foreign key CASCADE on results/category_scores handles child rows.
    const MAX_ITERATIONS = 20
    for (let i = 0; i < MAX_ITERATIONS; i++) {
      // Find the 50 oldest runs
      const { data: oldRuns, error: selectError } = await supabase
        .from("runs")
        .select("id")
        .order("created_at", { ascending: true })
        .limit(50)

      if (selectError || !oldRuns || oldRuns.length === 0) break

      const idsToDelete = oldRuns.map((r: { id: string }) => r.id)
      const { error: deleteError } = await supabase
        .from("runs")
        .delete()
        .in("id", idsToDelete)

      if (deleteError) {
        console.error("enforceStorageLimit: delete failed:", deleteError.message)
        break
      }

      console.log(`Deleted ${idsToDelete.length} old runs (batch ${i + 1})`)

      // Re-check size
      const { data: newSize, error: newSizeError } = await supabase.rpc(
        "get_leaderboard_size_bytes",
      )

      let currentBytes: number
      if (newSizeError || newSize === null || newSize === undefined) {
        const { count } = await supabase
          .from("runs")
          .select("*", { count: "exact", head: true })
        currentBytes = (count ?? 0) * 2048
      } else {
        currentBytes = Number(newSize)
      }

      if (currentBytes <= MAX_TABLE_BYTES) {
        console.log(
          `Storage trimmed to ${(currentBytes / 1024 / 1024).toFixed(1)} MB`,
        )
        break
      }
    }
  } catch (err) {
    // Storage enforcement is best-effort; don't fail the upload
    console.error("enforceStorageLimit: unexpected error:", err)
  }
}

/**
 * Enforces a maximum of 10 runs per device.
 *
 * Looks up the device by fingerprint_hash, fetches all runs for that device
 * ordered oldest-first, and deletes any beyond the 10 most recent.
 * CASCADE foreign keys on results/category_scores handle child rows.
 */
async function enforcePerDeviceRunLimit(
  supabase: ReturnType<typeof createClient>,
  fingerprintHash: string,
): Promise<void> {
  const MAX_RUNS_PER_DEVICE = 10

  try {
    const { data: deviceRow, error: deviceError } = await supabase
      .from("devices")
      .select("id")
      .eq("fingerprint_hash", fingerprintHash)
      .single()

    if (deviceError || !deviceRow) return

    const { data: deviceRuns, error: runsError } = await supabase
      .from("runs")
      .select("id, created_at")
      .eq("device_id", deviceRow.id)
      .order("created_at", { ascending: true })

    if (runsError || !deviceRuns || deviceRuns.length <= MAX_RUNS_PER_DEVICE) return

    const toDelete = deviceRuns
      .slice(0, deviceRuns.length - MAX_RUNS_PER_DEVICE)
      .map((r: { id: string }) => r.id)

    const { error: deleteError } = await supabase
      .from("runs")
      .delete()
      .in("id", toDelete)

    if (deleteError) {
      console.error("enforcePerDeviceRunLimit: delete failed:", deleteError.message)
    } else {
      console.log(`Trimmed ${toDelete.length} old runs for device ${fingerprintHash.slice(0, 8)}.`)
    }
  } catch (err) {
    // Per-device enforcement is best-effort; don't fail the upload
    console.error("enforcePerDeviceRunLimit: unexpected error:", err)
  }
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
    },
  })
}

function createServiceClient(): ReturnType<typeof createClient> {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")

  if (!supabaseUrl) {
    throw new Error("Missing required environment variable: SUPABASE_URL")
  }
  if (!serviceRoleKey) {
    throw new Error("Missing required environment variable: SUPABASE_SERVICE_ROLE_KEY")
  }

  return createClient(
    supabaseUrl,
    serviceRoleKey,
    { auth: { persistSession: false } },
  )
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}
