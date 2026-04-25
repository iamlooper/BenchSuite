# Section 6: UI / UX - Material Design 3 Expressive

## 6.1 Design Philosophy

- **Minimal screens, maximum substance** - few screens, each with enough information density to stand alone without nested menus.
- **Live benchmark execution is the primary interaction** - the Runner screen is the core product experience; progress must be legible and responsive in real time.
- **Dark-optimized** - default dark theme tuned for AMOLED. Material You dynamic color supported on Android 12+.
- **Numbers are primary** - scores and metrics are the product. Typography scale and hierarchy reinforce their importance over surrounding text.

## 6.2 Design System

- **Material Design 3 Expressive:** All components, motion, and theming follow MD3 Expressive specifications. Spring-based animations apply to score reveals, progress transitions, and screen navigation. `LoadingIndicator` replaces `CircularProgressIndicator`. `LinearWavyProgressIndicator` is used for active benchmark progress.
- **Single-activity Compose:** One `Activity`, one `NavHost`, zero `Fragment`s.
- **Component conventions:** All screen roots use `BenchScaffold` (wraps `Scaffold` inside `BenchBackdrop`). Major content blocks use `BenchHeroCard` (extraLarge corner radius, `surfaceContainerLow`, tonal elevation, accent border). Section separators use `BenchSectionLabel` (all-caps `labelLarge` in `primary`). Status chips and stat tags use `BenchMetricChip` (pill shape, `secondaryContainer`). Top bar actions use `BenchToolbarActionButton` (`FilledTonalIconButton`).

## 6.3 Color & Theme

- **Dynamic Color:** Uses Material You `dynamicDarkColorScheme()` / `dynamicLightColorScheme()` as primary on Android 12+.
- **Fallback Palette:** Deep midnight surface, glacier blue primary, mint green secondary, warm amber tertiary.
- **Score Tier Colors:** Four fixed colors mapped by score range: red (< 500), amber (500-999), green (1000-1499), blue (≥ 1500 or null/pending). Exposed via `scoreTierColor(score: Double?)`.
- **Pure Black mode:** Optional AMOLED optimization that replaces `background` and `surface` with `Color.Black` when dark theme is active.

## 6.4 Navigation

BenchSuite uses a **single-activity, single-back-stack, drill-down navigation** model via Jetpack Navigation Compose. There is no bottom navigation bar. All flows are hierarchical push/pop. The nav graph root is Home.

All route strings are typed constants in the `BenchDestination` sealed class inside `BenchSuiteNavGraph.kt`. No magic route strings are used outside that class.

Each screen receives only the navigation callbacks it needs - not the `NavController` directly. This keeps screens decoupled from navigation internals.

Destinations and entry points:

| Destination | Entered from |
|---|---|
| Home | Root |
| Runner | Home FAB |
| Results | Runner on completion; run history entry |
| CategoryDetail | Results category card |
| Leaderboard | Home |
| LeaderboardRunDetail | Leaderboard entry |
| LeaderboardCategoryDetail | LeaderboardRunDetail category card |
| Settings | Home top bar |
| About | Home top bar |

Back-stack rule: Runner is popped inclusive on completion (`popUpTo(Runner, inclusive = true)`), so Back from Results goes directly to Home without re-entering the runner.
