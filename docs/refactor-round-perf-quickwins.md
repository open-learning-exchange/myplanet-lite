# Refactor Round — Performance Quick Wins (12 granular PRs)

**Round budget:** ~10 PRs/day. 12 tasks listed so two can be dropped or deferred.
**Theme:** performance quick wins, micro-optimizations that unblock the bigger refactors, removal of obvious inefficiencies. No rewrites, no new abstractions, no unused code.
**Conflict policy:** every task below owns a **disjoint file set**. Nothing in this list edits a file that another task in this list edits. Land them in any order.

**Roadmap mapping**
| Roadmap item | Tasks |
|---|---|
| 1. Finish cleaning the data layer | T2, T3, T4 |
| 3. Expand ViewModel / use layers (prep) | T5 |
| 4. Complete DI cleanup | T2, T3, T4 |
| 7. Optimize remaining performance hotspots | T6, T7, T8, T9, T10, T11, T12 |
| 8. Improve code health and add tests | T1, T6, T7, T12 |

---

## T1 — Delete tracked merge artifacts from the test source set

**Files (2):**
- `app/src/test/java/org/ole/planet/myplanet/lite/DashboardVoicesFragmentTest.kt.orig`
- `app/src/test/java/org/ole/planet/myplanet/lite/DashboardVoicesFragmentTest.kt.patch`

**Problem:** Both files are committed to git (`git ls-files` confirms). They are leftover conflict-resolution artifacts sitting next to `DashboardVoicesFragmentTest.kt`. They get scanned by Gradle's source-set globbing and by every `grep`/IDE search, and they are a standing source of "which one is real?" confusion during merges.

**Do:** `git rm` both files. Optionally add `*.orig`, `*.rej`, `*.patch` to `.gitignore`.

**Do not:** touch `DashboardVoicesFragmentTest.kt` itself.

**Acceptance:** `git ls-files | grep -E '\.orig$|\.rej$|\.patch$'` is empty; `./gradlew testDebugUnitTest` still green.

**Risk:** none. **Size:** ~2 lines of diff.

---

## T2 — Route the two loose DI singletons at the shared OkHttp/Moshi instances

**Files (2):**
- `app/src/main/java/org/ole/planet/myplanet/lite/dashboard/SharedBitmapDependencies.kt` (lines 8–9)
- `app/src/main/java/org/ole/planet/myplanet/lite/dashboard/DashboardSurveysRepositoryProvider.kt` (line 10)

**Problem:** `AuthDependencies` already caches a single `OkHttpClient` + `Moshi` (`auth/AuthDependencies.kt:26-39`). These two singletons each build their **own** `OkHttpClient.Builder().build()`. Every extra `OkHttpClient` instance carries its own `ConnectionPool`, its own `Dispatcher` thread pool, and its own idle-connection keepalive threads — so avatar/post-image traffic and survey traffic never reuse a connection that login already opened. `SharedBitmapDependencies` additionally builds a second `Moshi` with `KotlinJsonAdapterFactory`, duplicating reflective adapter caches.

**Do:** Make both delegate to `AuthDependencies.client` / `AuthDependencies.moshi`.

**Do not:** change `AuthDependencies` itself, and do not touch the repository constructors (that is T3).

**Acceptance:** `assembleDebug` + `testDebugUnitTest` green; `grep -rn "OkHttpClient.Builder" dashboard/SharedBitmapDependencies.kt dashboard/DashboardSurveysRepositoryProvider.kt` returns nothing.

**Risk:** low — `AuthDependencies.resetForTesting()` already clears the cached client, so test isolation is preserved.

---

## T3 — Repository default constructor params should default to the shared client

**Files (3):**
- `dashboard/DashboardCoursesRepository.kt:34` — `internal val client: OkHttpClient = OkHttpClient.Builder().build()`
- `dashboard/DashboardSurveySubmissionsRepository.kt:27` — `private val client: OkHttpClient = OkHttpClient.Builder().build()`
- `dashboard/ServerConfigurationRepository.kt:22-27` — `client: OkHttpClient = OkHttpClient()` plus its own inline `Moshi.Builder()`

**Problem:** Same connection-pool/thread-pool duplication as T2, but on the **default argument** path — so every caller that omits `client` silently spins up a fresh HTTP stack. `ServerConfigurationRepository` also builds a private `Moshi`, re-doing reflective adapter generation for `ConfigurationResponse`.

**Do:** Change the *defaults only* to `AuthDependencies.client` / `AuthDependencies.moshi`. Keep the parameters injectable so `MockWebServer` tests keep passing their own client.

**Do not:** remove the parameters, change their names, or reorder them — tests construct these positionally.

**Acceptance:** `testDebugUnitTest` green (these three have existing repository tests that inject a client — they must be unaffected).

**Risk:** low. **Size:** ~6 lines.

---

## T4 — Collapse per-activity OkHttp/Moshi instances (and fix one that is missing the Kotlin adapter)

**Files (4):**
- `SplashScreen.kt:39-40`
- `SignupActivity.kt:78, 90`
- `MyPlanetLite.kt:78, 102`
- `dashboard/DashboardPostDetailActivity.kt:46`

**Problem:** Four screens each build their own `OkHttpClient` and `Moshi` as instance fields, so the cost is paid **per activity instance** — i.e. again on every rotation and every relaunch, on the main thread during `onCreate`. `DashboardPostDetailActivity:46` is the worst: `reusedMoshi` is a plain (non-lazy) field, so a full `Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()` runs during construction of every post-detail screen.

**Bonus correctness fix in the same diff:** `SplashScreen.kt:40` builds `Moshi.Builder().build()` **without** `KotlinJsonAdapterFactory`, unlike every other Moshi in the app. It is handed to `ServerConnectivityRepository`, so connectivity-response parsing is running on a differently-configured Moshi than the rest of the app.

**Do:** Point all four at `AuthDependencies.client` / `AuthDependencies.moshi`.

**Do not:** touch the repository classes these are passed into (T3 owns those).

**Acceptance:** `SplashScreenTest`, `SignupActivityTest`, `MyPlanetLiteTest` green; splash connectivity check still parses server metadata.

**Risk:** low–medium (four Robolectric-tested activities — run their tests specifically).

---

## T5 — Remove the redundant `withContext(Dispatchers.IO)` around `getStoredToken()`

**Files (6):**
- `CourseActivityLogger.kt:51`
- `DashboardCoursePageActions.kt:51, 76`
- `DashboardCourseDetailsBottomSheet.kt:174`
- `DashboardCoursePageFragment.kt:255`
- `DashboardTeamMembersFragment.kt:158`
- `survey/DashboardLocalSurveyRepository.kt:123, 164`

**Problem:** `AuthService.getStoredToken()` is already `suspend`, and `SecureTokenStorage.getToken()` already does its own `withContext(dispatcher)` with `dispatcher = Dispatchers.IO` (`auth/TokenStorage.kt:29, 40-43`). Every call site above wraps it in a *second* `withContext(Dispatchers.IO)`. That is a redundant continuation + dispatch hop on a call that happens on essentially every screen entry and every course/survey action.

**Do:** Unwrap to a direct `authService.getStoredToken()` call. Delete the now-unused `Dispatchers` / `withContext` imports only where nothing else in the file uses them.

**Do not:** change `TokenStorage` or `AuthService`. Do not touch the other ~17 `getStoredToken()` call sites that are already unwrapped or nested inside a larger IO block doing real work.

**Acceptance:** `testDebugUnitTest` green; no `withContext(Dispatchers.IO) { authService.getStoredToken() }` remains (`grep` it).

**Risk:** low. This is also direct prep for roadmap item 3 — these are exactly the call sites that move into a ViewModel later, and they are easier to lift once the dispatcher noise is gone.

---

## T6 — `CourseAdapter`: kill the O(n²) append and the per-keystroke re-filter allocations

**File (1):** `CourseAdapter.kt`

**Problem:**
1. `appendCourses()` (line ~205) does `newItems.filter { newItem -> items.none { it.id == newItem.id } }` — a nested linear scan, so appending a page of *m* courses against *n* loaded courses is **O(n·m)**. This runs on the main thread on every pagination page.
2. `applyFilter()` (line ~190) is called from **seven** entry points (`updateCategories`, `updateTagFilter`, `updateDownloadedCourses`, `updateDownloadProgress`, `updateCourseProgress`, `submitCourses`, `updateSearchQuery`) and each call rebuilds the entire `CourseListItem` list — including a fresh `CourseListItem.Header(categoriesProvider(), …)` — and re-runs `title.contains(searchQuery, ignoreCase = true)` over every item. `updateSearchQuery` fires on **every keystroke** with no debounce (`searchInput.addTextChangedListener`, line 231).
3. `updateCourseProgress()` does `items.indexOfFirst { it.id == courseId }` — another linear scan, called once per progress tick per course.

**Do (granular, in this one file):**
- Maintain a `MutableSet<String>` of loaded course ids alongside `items`; use it for the `appendCourses` dedupe and for `submitCourses`' `distinctBy`.
- Maintain a `MutableMap<String, Int>` id→index (or reuse the same set) for `updateCourseProgress`'s lookup.
- Add a short debounce (or an early-return on unchanged normalized query — the early return already exists at line ~186, keep it) so a keystroke that does not change the trimmed query does not rebuild the list.

**Do not:** restructure the sealed `CourseListItem` hierarchy, change the diff callback, or touch the ViewHolders. That is a separate, larger job.

**Acceptance:** `DashboardCoursesFragmentTest` green; scrolling a large course list and typing in search shows no new jank.

**Risk:** low–medium (single file, well covered by tests). **Size:** ~30 lines.

---

## T7 — `ResourceExplorerAdapter`: stop allocating a throwaway change payload per item

**File (1):** `DashboardResourcesPageFragment.kt` (lines ~205–245)

**Problem:** The adapter is constructed with

```
DiffUtils.itemCallback(
    areItemsTheSame = { old, new -> old.uniqueKey() == new.uniqueKey() },
    getChangePayload = { _, _ -> Any() }
)
```

`getChangePayload` returns a **fresh `Any()` for every changed item**, and the adapter never overrides the `onBindViewHolder(holder, position, payloads)` overload — so RecyclerView falls straight through to a full rebind anyway. The payload is pure allocation with zero effect. Separately, `downloadProgressByKey` is captured as an immutable constructor `Map`, which is why progress updates go through `submitList(newResources.toList())` (line 244) rather than a targeted item update.

**Do:**
- Drop the `getChangePayload` lambda entirely (the `DiffUtils.itemCallback` default already returns `null`), **or** — if partial rebind is actually wanted — return a real, meaningful payload constant and add the `payloads` overload that only re-binds the progress views. Pick one; do not leave both.
- Rely on `DiffUtils.itemCallback`'s default `areContentsTheSame = oldItem == newItem`, which is correct for `ResourceUi`.

**Do not:** rework the download-progress plumbing or the fragment's list-loading extensions in this PR.

**Acceptance:** `DashboardResourcesOnViewCreatedExtensionsTest` green; resource list still updates on download progress.

**Risk:** low. **Size:** ~5–15 lines depending on which option is chosen.

---

## T8 — `DashboardAvatarLoader`: stop wiping the shared "no avatar" negative cache on every construction

**File (1):** `dashboard/DashboardAvatarLoader.kt` (lines 31–53, 140–157)

**Problem:** `missingAvatars` is a **static** `mutableSetOf<String>()` in the companion — the app-wide memo of "this user has no avatar, don't ask again". But the `init` block does:

```
init {
    synchronized(missingAvatars) { missingAvatars.clear() }
}
```

A new `DashboardAvatarLoader` is constructed in at least five places (`DashboardVoicesFragment:182`, `DashboardTeamMembersFragment:160` and `:454`, `DashboardTeamMemberProfileActivity:129`, `DashboardPostDetailSessionExtensions:92`) — i.e. on essentially every fragment view creation and every tab switch. So the negative cache is emptied constantly and the app re-issues an HTTP GET to `/db/_users/org.couchdb.user:<name>/img` for every avatar-less user, on every screen, forever. On a team list of avatar-less members this is one wasted round trip per row per visit.

**Do:** Key the invalidation on what it is actually protecting against — a changed `baseUrl`. Keep a `@Volatile` companion field holding the base URL the negative cache was built for, and clear `missingAvatars` in `init` **only when the incoming `baseUrl` differs**. The comment in the code already states this is the intent ("so fresh base URLs or fixed endpoints can attempt avatar fetches again").

**Do not:** change the `AvatarUpdateNotifier` register/unregister flow, the `sharedCache` LruCache, or the `destroy()` contract. Keep `resetForTesting()` working.

**Acceptance:** `InviteMemberViewHolderTest` and `DashboardTeamMembersSupportTest` green; opening the team list twice issues avatar requests only on the first visit.

**Risk:** low. **Size:** ~10 lines.

---

## T9 — Downsample the toolbar avatar instead of decoding it full-size

**File (1):** `DashboardActivityPreferences.kt` (lines 56–68)

**Problem:** `refreshProfileSummary()` does `BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.size)` with **no `BitmapFactory.Options`** and hands the result to a small toolbar `ImageView`. A camera-sized profile photo therefore allocates a multi-megabyte `Bitmap` for a ~40dp target, on every dashboard resume.

**Do:** Two-pass decode — `inJustDecodeBounds = true`, then `inSampleSize`, then decode. The project **already has this exact helper**: `profile/ProfileActivityAvatarExtensions.kt:128 calculateInSampleSize(options, reqWidth, reqHeight)` and the two-pass pattern at lines 96–107. Reuse it rather than writing a new one; if visibility needs widening, promote the existing helper to `internal` (do not duplicate it).

**Do not:** add a new bitmap-utility file, and do not touch the avatar upload path in `ProfileActivityAvatarExtensions`.

**Acceptance:** `DashboardActivityTest` green; toolbar avatar renders identically.

**Risk:** low. **Size:** ~15 lines.

---

## T10 — Hoist per-call `Regex(...)` compilations to constants

**Files (4):**
- `OfflineCourseStorage.kt:104, 112, 121` — `Regex("[A-Za-z0-9]{1,5}")` compiled three separate times, each inside a called function
- `DashboardResourcesMediaUtils.kt:69, 89, 90` — `Regex("\\.pdf$", IGNORE_CASE)`, `Regex("[^a-zA-Z0-9\\-_]")`, `Regex("_+")` compiled per filename sanitised
- `dashboard/PostShareHelper.kt:205` — `"\\s+".toRegex()` compiled per share
- `util/MarkdownUtils.kt:50` — `Regex("!\\[([^\\]]*)\\]\\($escapedName\\)")`

**Problem:** `Regex(...)` compiles a `java.util.regex.Pattern` on every invocation. The codebase already establishes the right pattern — `MarkdownUtils.kt:8-13`, `PostShareHelper.kt:173-177`, `DashboardPostDetailActivity.kt:272-276` and `CreateVoiceActivity.kt:455-459` all hoist their patterns into `private val` companion constants. The sites above were simply missed. `DashboardResourcesMediaUtils`' three are on the file-naming path, hit once per resource upload/preview; `OfflineCourseStorage`'s three are on offline course reads.

**Do:** Move each *constant* pattern to a `private val` in the file's companion/top-level, matching the existing convention. `MarkdownUtils.kt:50` interpolates `$escapedName` so it cannot be a plain constant — leave it, or note it and skip.

**Do not:** change any regex semantics, and do not consolidate patterns across files.

**Acceptance:** `OfflineCourseStorageTest`, `DashboardResourcesMediaUtilsTest`, markdown/share util tests all green — these have near-100% coverage, so behaviour drift will be caught.

**Risk:** very low. **Size:** ~15 lines.

---

## T11 — `DashboardNewsAdapter`: avatar updates should not re-render the whole post

**File (1):** `DashboardNewsAdapter.kt`

**Problem:** `notifyAvatarUpdated()` (lines 73–82) ends in `recyclerView.post { positions.forEach(::notifyItemChanged) }`. `notifyItemChanged` with no payload triggers a **full** `DashboardNewsViewHolder.bind(item)`, which re-runs `markwon.setMarkdown(bodyView, item.message)` and re-binds every image via `imageBinder`. Markwon parsing + `Spanned` construction is the single most expensive thing in that bind. So one avatar arriving re-parses the markdown of every visible post by that author.

**Do:**
- Add an `AVATAR_UPDATE_PAYLOAD` constant next to the existing `DIFF_CALLBACK` companion.
- Pass it through `notifyItemChanged(position, AVATAR_UPDATE_PAYLOAD)`.
- Override `onBindViewHolder(holder, position, payloads)`; when the payload list contains only `AVATAR_UPDATE_PAYLOAD`, call just the avatar-binding path (`avatarBinder(avatarView, item.username, item.hasAvatar)`) and return. Otherwise `super`.

**Do not:** change `DIFF_CALLBACK`, the `usernameIndex` maintenance in `onCurrentListChanged`, or the pagination scroll listener.

**Acceptance:** `DashboardNewsViewHolderTest` and `DashboardVoicesFragmentTest` green; avatar arriving mid-scroll updates the image without the post body flashing.

**Risk:** low–medium (needs a small, focused method on the ViewHolder). **Size:** ~20 lines.

---

## T12 — `ServerOptionAdapterBase.getFilter()`: drop the per-keystroke full-list copy

**File (1):** `MyPlanetLiteSupport.kt` (lines 76–133)

**Problem:** The `Filter` implementation is a no-op filter that still pays full cost:

```
override fun performFiltering(constraint: CharSequence?) = FilterResults().apply {
    values = ArrayList(allItems)   // full copy, every keystroke
    count = allItems.size
}
```

`publishResults` then clears `visibleItems`, re-adds the same elements, and calls `notifyDataSetChanged()` — a full rebind of the server dropdown on every character typed into the server field, even though the visible set never changes. This runs on the login screen, the first thing users touch.

**Do:**
- Skip the defensive `ArrayList(allItems)` copy (the list is only read on the main thread here) — pass `allItems` through, or short-circuit `performFiltering` when the constraint produces the same set.
- In `publishResults`, return early without `notifyDataSetChanged()` when the published values are already what `visibleItems` holds.

**Do not:** introduce actual text filtering — the current always-show-everything behaviour is intentional (an `AutoCompleteTextView` acting as a dropdown) and is asserted by `ServerOptionAdapterTest`. Preserve it exactly.

**Acceptance:** `ServerOptionAdapterTest` and `MyPlanetLiteTest` green; server dropdown still lists all built-in + custom servers with dividers non-selectable.

**Risk:** low. **Size:** ~15 lines.

---

## Scheduling note

No two tasks above share a file, so all 12 can be open simultaneously. The only *ordering* preference is soft: **T2 → T3 → T4** are the same "one shared HTTP/JSON stack" theme, so reviewing them in that order reads better, but they touch disjoint files and can merge in any order.

Deliberately **excluded** from this round (bigger than a quick win, or would collide with several of the above):
- Introducing the first `ViewModel` (the app currently has zero) — roadmap item 3; T5 is the cheap prep for it.
- Adding an OkHttp disk `Cache` for images — real win, but needs a `Context` threaded into `AuthDependencies`, which collides with T2/T3/T4.
- Blanket `setHasFixedSize(true)` — zero call sites app-wide today, but landing it means touching ~6 fragments at once, which is exactly the merge-conflict surface this round is avoiding.
- Downsampling in `DashboardPostImageLoader` (`:120`, `:140` decode full-size into a 2MB `LruCache`) — same shape as T9 but on the hot feed path; worth its own round after T9 proves the helper.
