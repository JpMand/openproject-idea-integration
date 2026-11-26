---
name: intellij-plugin-review
description: >
  Review an IntelliJ Platform plugin for UI-freeze risks (EDT and threading model, read/write actions,
  coroutines), dynamic load/unload safety (always tested, even with require-restart) and internal or unstable
  API use (Plugin Verifier), checked against the targeted IDE branches. Use whenever the user asks to review,
  audit or check the plugin or a diff/PR of it, prepares a release or platform upgrade, or mentions freezes,
  EDT, dynamic plugins, unload leaks, the plugin verifier or internal API.
---

# IntelliJ Plugin Review

Review an IntelliJ Platform plugin the way a JetBrains platform engineer would. The goal is to catch, before users
do, three classes of defects that unit tests rarely show:

1. **UI freezes and threading violations.** Slow work on the EDT, model access without the right lock, the wrong
   dispatcher, swallowed cancellation.
2. **Broken dynamic load and unload.** The plugin can't be installed, updated or disabled without a restart, or it
   leaks its class loader. Check this **regardless of `require-restart`**. A plugin that requires restart today
   should know exactly what stops it from being dynamic, and a change must never quietly regress this.
3. **Internal or unstable API usage.** `@ApiStatus.Internal`, override-only, non-extendable, scheduled for removal,
   and binary incompatibilities with the targeted IDE builds.

## Ground rules

- **The review is read-only.** Never edit, stage, commit or push tracked files, and never "fix while reviewing"
  unless the user explicitly asks afterwards. Extra build configuration goes in a Gradle **init script** stored
  outside the repo. Put all scratch output (logs, clones, reports) in a review directory outside the repo:
  the session scratchpad if there is one, else `mktemp -d`. Call it `$REVIEW_DIR` below. Files that Gradle already
  writes under the ignored `build/` directory are fine. Why: the review must judge the code as it is and leave the
  working tree exactly as it found it.
- **Evidence over memory.** Platform behaviour changes from branch to branch, and training data lags. For example,
  the suspending `writeAction {}` runs on the **EDT in 261** but delegates to **`backgroundWriteAction` on
  `Dispatchers.Default` from 262 on**. Every finding must cite something checkable: a doc page, a file at a
  branch/tag of intellij-community, a commit, a YouTrack ID, a forum thread, a Plugin Verifier report line or an
  `idea.log` line. If you could not verify something, say so and label it *suspected*.
- **Version awareness.** Each rule applies to a branch range. A plugin that supports 261 to 263 must be correct on
  all of them. Code that only works on some of them is a finding.
- **Prefer the latest rule, but don't break the oldest supported build.** If the modern API (e.g. `Dispatchers.UI`,
  `AnActionEvent.coroutineScope`) doesn't exist at the plugin's `since-build`, recommend the best API that does
  exist, and note when to migrate.

## Step 0: Establish the build context

Read these before anything else:
- `gradle.properties`
- `build.gradle.kts` and `settings.gradle.kts`, including the IntelliJ Platform Gradle Plugin (IJPGP) version and
  the `pluginVerification` block
- `src/main/resources/META-INF/plugin.xml`, plus any optional-dependency config files, `<content>` modules and
  `xi:include`s
- CI workflows

Write down:

| Item | Where it comes from |
|---|---|
| Plugin ID and version, `require-restart` | `plugin.xml` / Gradle `pluginConfiguration` |
| `since-build` / `until-build` | `pluginConfiguration.ideaVersion` or `gradle.properties` |
| Compile platform (`platformVersion`) and exact build number | Gradle props; resolve via the releases feed below |
| Verifier target IDEs | `intellijPlatform.pluginVerification.ides`; with `recommended()`, the IDEs it resolves from the since/until range, as listed in the verifier output |
| Platform branches in scope | every branch from `since-build` to the newest targeted/verified IDE |
| Plugin dependencies and modules | `<dependencies>`, `<depends>`, `bundledPlugin(s)`, `bundledModule(s)`, `plugin(...)` |
| Review scope | the whole codebase, or a diff/PR/branch if the user named one |

Branch numbers come from the marketing version: `20YY.N` maps to `YYN`, so 2026.1 is **261**, 2026.2 is **262** and
2026.3 is **263**. To resolve an exact build number for a version, use the JetBrains releases feed:
```bash
curl -sS 'https://data.services.jetbrains.com/products/releases?code=IIU&type=release,rc,eap&latest=false' \
  | python3 -I -c 'import json,sys; [print(r["type"], r["version"], r["build"], r["date"]) for r in json.load(sys.stdin)["IIU"][:15]]'
```
When the user mentions specific versions (e.g. "2026.2 or 2026.3"), they are the primary targets. Every source
lookup must then use the matching branch (`262`, `263`) or the release tag (`idea/263.7307.200`), never `master`.
Use `master` only to warn about upcoming changes, and label it as such.

When the scope is a diff, apply the manual threading review to the changed code and to what it calls or is called
by. Still run the verifier and the reload gate on the **whole plugin**: a one-line change can break unload.

## Step 1: Refresh the rule book

Don't re-read everything on every run. Read the must-read pages, check what changed recently, and research
specific questions as they come up.

**SDK docs** (https://plugins.jetbrains.com/docs/intellij/, repo `JetBrains/intellij-sdk-docs`). These are the
rule book. Clone without blobs and check out only what you need:
```bash
git clone -q --depth 200 --filter=blob:none --no-checkout https://github.com/JetBrains/intellij-sdk-docs.git "$REVIEW_DIR/sdk-docs"
cd "$REVIEW_DIR/sdk-docs"
P='topics/basics/architectural_overview/threading topics/basics/plugin_structure/dynamic_plugins.md topics/basics/disposers.md
   topics/basics/plugin_structure/plugin_services.md topics/basics/plugin_structure/plugin_listeners.md topics/basics/action_system.md
   topics/appendix/api_internal/api_internal.md topics/appendix/verifying_plugin_compatibility.md
   topics/appendix/api_notable reference_guide topics/appendix/tools/intellij_platform_gradle_plugin topics/basics/ide_development_instance.md'
git checkout -q HEAD -- $P
git log --since='6 months ago' --date=short --format='%h %ad %s' -- $P | head -50   # what changed lately and why
```
Must-read, in this order:
- `threading/threading_model.md`, `threading/background_processes.md`, `threading/debounced_updates.md`
- `threading/coroutines/*` (dispatchers, scopes, launching, tips)
- `dynamic_plugins.md` and `disposers.md`
- the `getActionUpdateThread` section of `action_system.md`
- `api_internal.md`, including its replacement and **Exceptions** tables
- `api_notable/api_notable_list_20YY.md` and `reference_guide/api_changes_list_20YY.md` for every year in scope.
  These list per-release threading and API changes.

Don't `git grep` across a blobless clone: it fetches every blob lazily and runs for many minutes. Check out the
paths first, then grep the files.

**intellij-community** (`JetBrains/intellij-community`) is the actual behaviour. Use it to confirm what an API does
on each target branch:
```bash
# One file at a branch or tag:
curl -sS "https://raw.githubusercontent.com/JetBrains/intellij-community/263/platform/core-api/src/com/intellij/openapi/application/coroutines.kt"
# Several files: shallow, blobless, sparse checkout of one branch:
git clone -q --depth 1 --filter=blob:none --no-checkout --branch 263 https://github.com/JetBrains/intellij-community.git "$REVIEW_DIR/ic-263"
git -C "$REVIEW_DIR/ic-263" ls-tree -r --name-only HEAD | grep '/DynamicPlugins'   # find paths (trees are present)
git -C "$REVIEW_DIR/ic-263" sparse-checkout set --no-cone 'platform/platform-impl/src/com/intellij/ide/plugins/*'
git -C "$REVIEW_DIR/ic-263" checkout -q
# Commit history and reasoning (treeless: commits only, a few MB and seconds):
git clone -q --filter=tree:0 --no-checkout --single-branch --branch 263 --shallow-since=2026-01-01 \
  https://github.com/JetBrains/intellij-community.git "$REVIEW_DIR/ic-log-263"
git -C "$REVIEW_DIR/ic-log-263" log --format='%h %ad %s' --date=short --grep='IJPL-234915'
# First build of a branch that contains a fix:
git -C "$REVIEW_DIR/ic-log-263" fetch -q --filter=tree:0 --shallow-since=2026-01-01 origin 'refs/tags/idea/263.*:refs/tags/idea/263.*'
git -C "$REVIEW_DIR/ic-log-263" tag --contains <sha> | sort -V | head -1
```
Branches are named `261`, `262`, `263` and tags `idea/<build>`; `git ls-remote` lists them. Commit subjects usually
carry the YouTrack ID (`[ui] IJPL-234915: ...`), so grep history by ID to see when and why behaviour changed.
Before calling a platform API internal, deprecated or behaving a certain way, check its annotations and KDoc
**on each target branch**.

**YouTrack** (https://youtrack.jetbrains.com, projects **IJPL** for the platform and **IDEA**). The public REST
API needs no token:
```bash
curl -sS -G 'https://youtrack.jetbrains.com/api/issues' \
  --data-urlencode 'query=project: IJPL StatusBarWidgetFactory unload' \
  --data-urlencode 'fields=idReadable,summary,resolved,customFields(name,value(name))' --data-urlencode '$top=10'
```
Read `State`, `Available in` / `Included in builds` (the fix versions) and `Subsystem`. Tag names differ between
projects, so prefer free-text queries plus `project:` over tag filters. A fix that is "Available in 2026.1" may
still be missing from the earliest 261 builds; confirm with the tag check above. Report fixed-but-not-everywhere
issues as findings, with the minimum safe build.

**JetBrains Platform forum** (https://platform.jetbrains.com, Discourse). This is where community symptoms and
workarounds show up first:
```bash
curl -sS -G 'https://platform.jetbrains.com/search.json' --data-urlencode 'q=plugin unload status bar widget'   # .topics[].id/slug
curl -sS 'https://platform.jetbrains.com/t/<topic-id>.json'                                                      # .post_stream.posts[].cooked
```
Threads often link to the YouTrack issue that tracks the fix. Follow it, and check the fix version against the
branches in scope.

**IntelliJ Platform Explorer**
(`https://plugins.jetbrains.com/intellij-platform-explorer/extensions?extensions=<EP FQN>`, e.g.
`com.intellij.statusBarWidgetFactory`) shows how other plugins implement an extension point. The page is rendered
client-side; if a plain fetch returns an empty shell, use a browser or web-fetch tool, or search GitHub code for the
EP name. Check each sample's `since-build` and last update, and ignore implementations that target builds older
than the plugin's or that use APIs since deprecated. JetBrains' own implementations in intellij-community, on the
right branch, are the most reliable reference.

When the docs and the code disagree, the code on the target branch wins, and the gap is worth noting. Example:
`dynamic_plugins.md` says the unload heap dump goes to the user home, but 263 writes
`unload-<pluginId>-<date>.hprof` to the IDE **log** directory.

## Step 2: Gate A, Plugin Verifier (internal API and compatibility)

Run the verifier on the whole plugin against every branch in scope. First runs download IDEs; that's expected.
```bash
./gradlew verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin --continue --console=plain \
  2>&1 | tee "$REVIEW_DIR/verifyPlugin.log"
```
- Read the IDEs that were actually verified from the log and the report directories. If they miss a branch in scope
  (or the latest release or EAP of it), say so and mark that branch's verification *not run*. Don't edit the build
  file.
- Parse the per-IDE reports (`find build/reports/pluginVerifier -type f`). The files that matter:
  - `internal-api-usages.txt` and `internal-api-kt-usages.txt`
  - `compatibility-problems.txt`, and any compatibility-warnings file
  - `override-only-usages.txt` and `non-extendable-api-usages.txt`
  - `deprecated-usages.txt` (look for scheduled-for-removal entries) and `experimental-api-usages.txt`
  - `plugin-structure-warnings.txt`, `dependencies.txt` and `verification-verdict.txt`
- The **dynamic-plugin verdict is only on the console**, and in `report.md` if Markdown reports are enabled. Find
  it with `grep -E 'Plugin (can probably|probably cannot) be enabled or disabled without IDE restart' "$REVIEW_DIR/verifyPlugin.log"`.
  The failure line names the reason.
- **Severity.** Blocker: internal API usage, compatibility problems, missing dependencies, invalid plugin. Major:
  override-only or non-extendable misuse, and scheduled-for-removal APIs (name the removal version). Minor:
  deprecated API with a known replacement. Info: experimental API, which may be deliberate; note which branches
  stabilise it, using `stabilized-experimental-apis` if present.
- **Confirm every internal-API hit at the source** on each branch (the annotation may differ between branches).
  Look up the replacement in `api_internal.md`. Check its Exceptions table and known false positives, e.g.
  `ToolWindowManager.registerToolWindow(String, ...)`, MP-6705. Search YouTrack (subsystem *Internal API*) for an
  open replacement request.
- **Check the CI gate.** Recent IJPGP defaults `failureLevel` to `COMPATIBILITY_PROBLEMS`, `INTERNAL_API_USAGES`
  and `OVERRIDE_ONLY_API_USAGES`. Check the project's IJPGP version and configuration. If internal API or
  non-dynamic status would not fail CI, recommend an explicit `failureLevel` that includes `INTERNAL_API_USAGES`
  and `NOT_DYNAMIC`. Note that `NOT_DYNAMIC` cannot be combined with `teamCityOutputFormat = true`.
- **Look for what the verifier can't see.** It only checks bytecode against the IDE. Grep the sources for:
  - reflection into the platform (`Class.forName`, `getDeclared*`, `setAccessible`, `MethodHandles`)
  - `com.intellij.*Impl` classes
  - `@Suppress("UnstableApiUsage" | "INVISIBLE_MEMBER" | "INVISIBLE_REFERENCE")`
  - `@OptIn(...Internal...)`
  - casts to implementation classes
  - platform `internal` Kotlin declarations
  Platform internals that are reached only through reflection still break without warning.

## Step 3: Gate B, dynamic load and unload

Run both parts even when `require-restart="true"`. In that case, report what *would* block dynamic loading and
whether the attribute is still justified.

### B1. Static check

**Descriptor.** These items are the conditions that the platform's own validator and the verifier check:
- no `<application-components>`, `<project-components>` or `<module-components>`
- every `<group>` has an `id`, and every `<action>` has an `id` or `class`
- no service declared with `overrides="true"`
- every **extension point used is `dynamic="true"`**. Check each EP on every target branch: find its
  `<extensionPoint ... dynamic="true">` declaration in intellij-community, or in the owning plugin.
- the plugin's own EPs are declared `dynamic="true"`, and their consumers never cache `extensionList` results
  without listening for changes
- any `Configurable` that depends on EPs implements `Configurable.WithEpDependencies`

**Code: anything that keeps the plugin class loader reachable after unload.** For each hit, say what holds the
reference and what the correct owner is:
- `object` / `companion object` / top-level `val` state that holds a `Project`, PSI, `Editor`, `VirtualFile`, Swing
  components, listeners, lambdas, `Job`s, executors or caches keyed by plugin classes. Stateless `object`s are fine.
- Listeners and connections without a parent disposable: `messageBus.connect()` with no argument,
  `addXxxListener(l)` without a `Disposable` overload, `EditorFactory` or `VirtualFileManager` listeners, AWT event
  listeners, `KeyboardFocusManager` listeners.
- Wrong `Disposer` parents. **`Project` and `Application` must never be used as parent disposables in plugin code**;
  use a plugin service instead. Also flag `Disposer.newDisposable()` without a parent.
- Coroutines outside the plugin's lifetime:
  - `GlobalScope`
  - hand-built `CoroutineScope(...)` that are never cancelled
  - `Application.coroutineScope` / `Project.coroutineScope` (deprecated, and they leak project or plugin)
  - scopes handed in by the platform whose plugin binding depends on the build, e.g. the scope passed to
    `StatusBarWidgetFactory.createWidget(project, scope)`, which became plugin-bound only with IJPL-234915
    (first 261 build: 261.21525.39)
  The correct pattern is a light service that receives its `CoroutineScope` by constructor injection.
- Threads, executors, `java.util.Timer`, `Alarm` without a disposable, `ScheduledExecutorService` and HTTP clients
  (e.g. a Ktor `HttpClient` with its engine threads) must be shut down in a service's `dispose()`.
- JVM-global registrations: `Runtime.addShutdownHook`, `Authenticator.setDefault`, `ProxySelector.setDefault`,
  `URL.setURLStreamHandlerFactory`, `UIManager.put` with plugin classes, `ThreadLocal`s on pooled threads, JUL
  handlers, `ServiceLoader` or serializer caches in **platform-provided** libraries that retain plugin classes.
- PSI held in long-lived fields (use `SmartPsiElementPointer`), `Language` or `FileType` used as map keys, and user
  data put on long-lived holders (`Project`, `Editor`, `VirtualFile`) that is never removed. Clean these up in
  `DynamicPluginListener.beforePluginUnload`. Veto via `checkUnloadPlugin` only while an operation is running.
- **The platform's own `DynamicPlugins` API is `@ApiStatus.Internal`**. The plugin must not call it.

### B2. Runtime reload in a sandbox IDE

This is the only check that proves the class loader is actually released. The method mirrors the documented
auto-reload workflow: a sandbox IDE with auto-reload watches its plugins directory. When the plugin JAR's
content changes, it unloads and reloads the plugin. This review adds leak snapshots, internal-mode assertions and
an idle background VFS refresh, so no window focus is needed.

Requirements:
- a display, which in containers means `Xvfb`/`xvfb-run`
- several GB of disk and memory
- the IntelliJ Platform Gradle Plugin's `runIde`
- platform **262 or newer** for the idle-refresh trigger. On 261, the refresh fires only when the IDE frame is
  activated, so either run the test on a newer platform (e.g. `-PplatformVersion=2026.2.4`; the build needs a
  matching `tasksPluginVersion.<branch>` in `gradle.properties`) or mark the 261 runtime check *not run*.

1. Write `$REVIEW_DIR/reload.init.gradle.kts`. It only adds JVM flags and a project to open, and doesn't touch the
   build:
   ```kotlin
   allprojects {
       tasks.matching { it.name == "runIde" }.configureEach {
           val exec = this as JavaExec
           exec.jvmArgs(
               "-Didea.auto.reload.plugins=true", "-Didea.is.internal=true",
               "-XX:+UnlockDiagnosticVMOptions",
               "-Dide.plugins.snapshot.on.unload.fail=true", "-Dide.plugins.analyze.snapshot=true",
               "-Dvfs.background.refresh.on.idle=true",
               "-Didea.trust.all.projects=true", "-Djb.consents.confirmation.enabled=false",
               "-Djb.privacy.policy.text=<!--999.999-->", "-Didea.initially.ask.config=never",
               "-Dide.show.tips.on.startup.default.value=false",
           )
           System.getenv("REVIEW_OPEN_PROJECT")?.let { exec.args(it) }
       }
   }
   ```
   Registry keys can be set as `-D` system properties: in 263, `RegistryValue` falls back to `System.getProperty`.
   Internal mode turns on `SlowOperations` and other threading assertions, so this run also catches threading bugs.
2. Build the sandbox, create a throwaway project, and start the IDE **as a background task**. Use the harness's
   background mode, not a bare `&`, so it can be stopped later:
   ```bash
   ./gradlew prepareSandbox --console=plain > "$REVIEW_DIR/prepareSandbox.log" 2>&1
   mkdir -p "$REVIEW_DIR/reload-project" && echo '# reload test' > "$REVIEW_DIR/reload-project/README.md"
   REVIEW_OPEN_PROJECT="$REVIEW_DIR/reload-project" xvfb-run -a -s '-screen 0 1920x1080x24' \
     ./gradlew runIde --init-script "$REVIEW_DIR/reload.init.gradle.kts" --console=plain > "$REVIEW_DIR/runIde.log" 2>&1
   ```
3. Locate the log and the main plugin JAR (the one that contains `META-INF/plugin.xml`):
   ```bash
   LOG=$(find build/idea-sandbox -path '*/log/idea.log' -newer "$REVIEW_DIR/prepareSandbox.log" | head -1)
   for j in $(find build/idea-sandbox -path '*/plugins/*/lib/*.jar'); do unzip -l "$j" | grep -q 'META-INF/plugin.xml' && JAR=$j; done
   ```
4. Wait, with a bounded poll (use a Monitor or until-loop tool if foreground `sleep` is blocked), until `idea.log`
   shows the plugin loaded (`Loaded custom plugins: ... <Plugin Name>`) and the project opened. Then give it about
   60 seconds of idle time, so that startup activities, services and status-bar widgets are created.
5. Change the JAR's **content** in place. A new entry is enough. A renamed or new JAR doesn't trigger a reload.
   ```bash
   date > "$REVIEW_DIR/review-reload-marker.txt" && zip -q -j "$JAR" "$REVIEW_DIR/review-reload-marker.txt"
   ```
6. Poll `idea.log` for up to about 3 minutes. The reload has started when you see
   `Detected plugin .jar file change ..., reloading plugin`. It **failed** if any of these appear:
   - `cannot be unloaded dynamically:` or `cannot be loaded dynamically:` (the reason follows)
   - `Plugins that were expected to unload but are still referenced`
   - `is not unload-safe because class loader cannot be unloaded`
   - `Snapshot analysis result:`, which gives the GC-root chain to the leak. Quote it.
   - `scope was not completed in`, which means a leaked coroutine; quote the coroutine dump.
   - `Unloading failed`
   - `Reconfiguration is not possible`
   It **passed** if the change was detected, none of these appeared, and the plugin is loaded again with no new
   errors. Run a **second cycle** (step 5 again). Leaks that build up across load cycles only show on the second
   unload.
7. Collect threading evidence from the same run:
   - `ERROR`/`SEVERE` entries whose stack trace contains the plugin's packages, especially messages about EDT, read
     or write access, slow operations, `ActionUpdateThread`/`OLD_EDT`, modality or write-unsafe context
   - freeze dumps: `find build/idea-sandbox -path '*/log/threadDumps-freeze-*'`
   - any `unload-*.hprof` files
8. Stop the IDE: stop the background task, and as a last resort `pkill -f idea-sandbox`. Leave the sandbox under
   `build/` as it is.

This run covers what is created at startup: services, listeners, startup activities, status-bar widgets and
actions. UI the plugin only creates on demand, such as tool-window content and dialogs, is not exercised.

## Step 4: Manual threading review

Work through the code (or the diff and its call sites) with the rules below. Search with the grep tool first, then
read each hit in context. The rule numbers are referenced in the report.

```text
runBlocking|runBlockingCancellable|runBlockingMaybeCancellable|invokeAndWait|Thread\.sleep|\.join\(\)|\.get\(\)|await\(|waitFor\(
executeOnPooledThread|SwingUtilities\.invoke|invokeLater|ModalityState\.(any|NON_MODAL|nonModal)|GlobalScope|CoroutineScope\(
Dispatchers\.(Main|IO|EDT|UI|Default)|limitedParallelism|withContext|launch\(|currentThreadCoroutineScope|coroutineScope
ReadAction\.|runReadAction|readAction|smartReadAction|nonBlocking|WriteAction\.|runWriteAction|writeAction|WriteCommandAction|edtWriteAction|backgroundWriteAction
getActionUpdateThread|ActionUpdateThread|OLD_EDT|override fun update\(|actionPerformed
catch \(\w+: (Throwable|Exception)\)|runCatching|ProcessCanceledException|CancellationException
messageBus|connect\(|subscribe\(|addListener|Disposer\.|Disposable|@Service|ProjectActivity|StartupActivity
PasswordSafe|HttpRequests|HttpClient|ktor|OkHttp|Files\.|File\(|ProcessBuilder|GeneralCommandLine
```

- **T1. No blocking on the EDT.** This rules out network, file and process I/O, `Future.get`, latches,
  `Thread.sleep`, `PasswordSafe`/credential access, heavy JSON parsing, PSI/VFS/index traversal, and
  `runBlocking*` or `runWithModalProgressBlocking` used to fake synchronous calls. Since **2026.1**,
  `OSProcessHandler.waitFor()` on the EDT or under a read lock logs an error for all users. Trace each EDT entry
  point: listeners, `actionPerformed`, `update`, renderers, Swing callbacks, `Dispatchers.EDT` blocks, `invokeLater`
  bodies and `Disposable.dispose`.
- **T2. Swing only on the EDT.** Create and mutate components only on the EDT. For pure UI work, use
  `Dispatchers.UI`, which holds no lock; read or write actions inside it are reported as errors. Use
  `Dispatchers.EDT` when the EDT code touches the model; it holds the write-intent lock. Since 2025.1,
  `Dispatchers.Main` is pure UI and forbids model access, so prefer `Dispatchers.EDT`/`UI`.
- **T3. Dispatcher discipline.** Wrap `Dispatchers.IO` around the I/O call only, not around parsing or business
  logic. CPU work goes on `Dispatchers.Default`. Store `limitedParallelism(n)` once in a property; creating it per
  call doesn't limit anything. Use a `Mutex`, not `limitedParallelism(1)`, for mutual exclusion. Use `withContext`
  rather than nested `launch` ping-pong between dispatchers.
- **T4. Read actions.** Model reads (PSI, VFS, project model, indexes) outside the EDT need a read action.
  - Prefer the suspending `readAction {}`, or `smartReadAction(project) {}` when indexes are involved. These are
    write-allowing and cancellable.
  - The blocking `ReadAction.compute/run` and `runReadAction` are **deprecated since 2026.1**.
    `ReadAction.computeCancellable` is **deprecated in 263**, and `nonBlocking(Runnable)` is deprecated as well.
  - In Java or blocking code, use `ReadAction.nonBlocking(callable).expireWith(..).inSmartMode(..).submit(..)`.
    `readActionBlocking` and `ReadAction.computeBlocking` are a last resort, used under modal progress.
  - Re-check validity (`isValid`) at the start of every read action.
- **T5. Write actions are version-sensitive.** In **261**, `writeAction {}` is `@Experimental` and runs on the EDT.
  From **262** it is `backgroundWriteAction` on `Dispatchers.Default`. For code that spans these branches:
  - use **`edtWriteAction {}`** when the block touches UI or relies on the EDT
  - use `backgroundWriteAction {}` only for UI-free model writes
  - use `WriteCommandAction`/`writeCommandAction` for undoable document or PSI edits
  Keep write scopes minimal and do the computation beforehand in a read action. Never write from renderers,
  `update()`, `SwingUtilities.invokeLater` or `ModalityState.any()`.
- **T6. Modality.** Model-touching EDT work goes through `Application.invokeLater` with a deliberate
  `ModalityState`; `defaultModalityState()` is the usual choice and `any()` is only for pure UI. When launching a
  coroutine, attach the modality once (`launch(ModalityState.current().asContextElement())`) instead of switching it
  mid-coroutine. Use `DumbService.smartInvokeLater` when index access is needed.
- **T7. Actions.**
  - Every `AnAction` overrides `getActionUpdateThread()`. Use `BGT` unless `update()` must touch Swing; `OLD_EDT` is
    deprecated. A BGT `update()` must not touch Swing; read context through `e.getData(...)` or `UiDataProvider`.
  - `update()` must be fast and side-effect free: no I/O, no network, no expensive service initialisation.
  - Long-running `actionPerformed` work goes to `currentThreadCoroutineScope().launch {}` (2024.2+),
    `AnActionEvent.coroutineScope` (**2026.1+**) or a service scope, with a background progress where the user
    should see it.
- **T8. AWT input handlers (2026.1+).** `KeyListener`, `MouseAdapter` and similar handlers no longer run under
  the write-intent lock. Model access inside them needs an explicit read action or a coroutine.
- **T9. Coroutine scopes.**
  - Use a service-injected scope: `@Service class S(private val cs: CoroutineScope)`, or
    `(project: Project, cs: CoroutineScope)` for project services.
  - Don't use `GlobalScope`, `Application/Project.coroutineScope` or hand-made scopes.
  - `runBlockingCancellable` and `runBlockingMaybeCancellable` are only for background bridging of legacy APIs (not
    recommended) and must never run on the EDT. Check each overload's status: the two-argument variants are
    `@Internal`.
- **T10. Cancellation.** Never swallow `CancellationException` or `ProcessCanceledException`. `catch (e: Exception)`,
  `catch (Throwable)` and `runCatching {}` in suspending or progress code must rethrow them. Long loops call
  `checkCanceled()` or `ensureActive()`. Don't log cancellation as an error.
- **T11. Background work and progress.** Use `withBackgroundProgress(project, title) {}` or `withModalProgress` in
  coroutines, and `Task.Backgroundable` in Java. A bare `executeOnPooledThread` for user-visible or long work has no
  cancellation or progress; replace it with a coroutine in a service scope.
- **T12. Listeners and events.** Handlers stay light (ideally they only invalidate caches). Debounce heavy
  follow-up work with `MergingUpdateQueue` or Flow `debounce`, and compute it with non-blocking reads. UI-free VFS
  listeners can use `VFS_CHANGES_BG`, `BulkFileListenerBackgroundable` or `addAsyncFileListenerBackgroundable`
  (2026.1+).
- **T13. Services and startup.**
  - Service constructors stay cheap: no I/O, no `runBlocking`, no cascading `getService` chains.
  - Application services never store a `Project`. Light services are `final` classes.
  - Use `serviceAsync()` in suspending code. Startup work uses the suspending `ProjectActivity`. `StartupActivity`
    is legacy, and `ApplicationLoadListener` is gone in 2026.3.
- **T14. Renderers and painting.** Cell renderers, `paintComponent` and `getPreferredSize` do no I/O, model access,
  allocation storms or service calls; precompute data in the background.
- **T15. Shared mutable state** crossing threads must be thread-safe: immutable snapshots, `@Volatile`/atomics, or
  confinement to one dispatcher. Flag check-then-act races between the EDT and background coroutines, such as
  "latest request wins" logic without cancelling the previous `Job`.

For every suspected violation, trace whether the code actually runs on the thread in question; follow callers up
to the platform entry point. Where the thread is ambiguous, recommend documenting the contract with `@RequiresEdt`,
`@RequiresBackgroundThread`, `@RequiresReadLock` or `@RequiresBlockingContext`
(`com.intellij.util.concurrency.annotations`) so that DevKit inspections can check the call sites. If the IDE or
Qodana is available, the DevKit inspections are worth mirroring: *ActionUpdateThread is missing*, *Plugin.xml
dynamic plugin verification*, *Map key may leak*, *Incorrect parentDisposable parameter*, *Non-default constructors
for service and extension class*, and *Forbidden in suspend context method usage*.

## Step 5: Cross-check before reporting

For every finding above *Info*:
1. Confirm the rule on the target branch, in the docs and in the intellij-community code (cite path@branch).
2. Search YouTrack and the forum for the symptom or API. A platform bug changes the advice: the fix becomes a
   workaround plus the minimum build that contains the fix. A fixed-in-later-build issue means the plugin should
   either guard the code or raise `since-build`.
3. When proposing an implementation pattern for an extension point, check how JetBrains, and current plugins in the
   Platform Explorer, implement it on the same branch.
4. Drop findings you can't support. Mark the rest **Confirmed (tool)**, **Confirmed (source)** or **Suspected**.

## Step 6: Report

Reply in chat with the report below, and also save it as `$REVIEW_DIR/review.md`. Don't add it to the repository.
Lead with the verdict. Keep each finding concrete: say where, what breaks for the user, and the fix, as a code
sketch where it helps.

```markdown
# Plugin review: <plugin id> <version> @ <git short sha> (<scope>)

## Context
Targets: since <build> / until <build or open>; compiled against <version (build)>; verified against <IDEs>.
Branches in scope: 261, 262, 263 (primary: 262, 263). IJPGP <ver>. require-restart: <true|false>.
Sources: intellij-sdk-docs @ <sha, date>; intellij-community <branch/tag list>.

## Verdict
| Gate | Result | Evidence |
|---|---|---|
| A. Internal/unstable API and compatibility (Plugin Verifier) | PASS/FAIL/NOT RUN | report paths, counts per IDE |
| B1. Dynamic plugin, static | PASS/FAIL | verifier dynamic line, descriptor and code checklist |
| B2. Dynamic plugin, runtime reload | PASS/FAIL/NOT RUN (why) | idea.log lines, hprof analysis |
| C. Threading model | PASS/ISSUES | rules T1–T15 hit counts |

## Findings
### [F1] <Blocker|Major|Minor|Info>: <short title>  (<Threading T4 | Dynamic | Internal API | Compatibility>)
- Where: `path/File.kt:123` (and call path, if relevant)
- Problem: <what happens: freeze, leak, crash, breaks on 263...>; branches affected: <...>
- Rule / evidence: <doc link or path@branch>, <commit / IJPL-xxxx / forum thread>, <log or report line>
- Fix: <specific change; code sketch>; minimum build if relevant
- Confidence: Confirmed (tool) | Confirmed (source) | Suspected

## Upstream issues to watch
| Issue | State | Fixed in | Relevance |

## Not checked / how to run locally
<e.g. runtime reload on 261 requires window focus: run `runIde`, use the feature, run `buildPlugin`, focus the sandbox IDE,
expect the "reloaded successfully" notification; or Settings | Plugins → disable without restart>
```

Severity guide:
- **Blocker:** internal API or compatibility problems; unload failure or class-loader leak; model writes outside a
  write action or off the allowed thread; network or disk I/O or process waits on the EDT; Swing touched off the
  EDT.
- **Major:** deprecated blocking read actions on long background paths; missing `getActionUpdateThread`;
  swallowed cancellation; unbounded or wrong-scope coroutines; listeners without disposables; a behaviour that
  differs between supported branches.
- **Minor:** obsolete API that still works, with a clear replacement; scope or style issues with no user-visible
  effect.
- **Info:** experimental API, upcoming changes on master, suggestions.

End with the top three fixes by impact. Offer to apply them; don't apply them unless the user asks.
