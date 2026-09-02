# IJava 2026 Upgrade Plan — Deep Audit & SOTA Roadmap

- Date: 2026-09-01
- Branch: `feature/update2026`
- Audit scope: entire repository at `4ff77a2` + uncommitted `feature/update2026` work
- Legend: **[V]** = verified in code/artifacts during this audit · **[I]** = inferred recommendation

---

## 1. Executive Summary

IJava is a functional, feature-rich Jupyter kernel for the JVM (JShell-based), but in
2026 it lags SOTA in five areas:

1. **Reliability** — statement timeouts leak threads (timed-out code keeps running in the
   background), and a 2019-era ZMQ message loop serializes *all* message types behind
   running cells, freezing completions/hover while a cell executes.
2. **Packaging correctness** — the shipped fat jar contains literal `@version@`/`@project@`
   placeholders; `kernel_info`/banner report a bogus version, and the `kernel-metadata.json`
   inside the jar belongs to the `jupyter-jvm-basekernel` dependency (2.3.0), so the banner
   claims "implementation by jupyter-jvm-basekernel".
3. **Quality gates** — JUnit 4, ~580 lines of tests, no coverage gate, no lint/static
   analysis, no dependency verification (commented out), and CI runs **only on tag push**
   on a self-hosted runner — no PR checks, no JDK matrix.
4. **Security posture** — no sandboxing or isolation story, network-capable Maven resolver
   with hardcoded remotes, process magics (`cmd`, `git-mermaid`) with unbounded execution
   and a stream-deadlock bug, file-writing magics, no memory limits in `kernel.json`.
5. **Performance headroom** — JShell is fine as a default engine but per-statement
   recompilation, per-compile classloader churn in the compile magic, deferred startup
   script evaluation (first cell pays a startup penalty), and a fixed 50 ms loop sleep cap
   responsiveness. Java 25 (LTS) features (virtual threads, structured concurrency,
   ScopedValue) are unused; the build targets Java 21 only.

The plan below is ordered as **P0 quick wins (≤ 1 day total)**, **P1 high-impact
(2–4 weeks)**, **P2 strategic (quarter+)**. It is designed so every item lands as a
separate reviewable branch/PR, with a validation gate at each step.

**Headline recommendation:** keep JShell as the default execution engine (it is the right
default for a teaching/interactive kernel), put it behind a small engine SPI so a
JDT/Compiler-API incremental engine can be added without forking the user base, decouple
the message loop (fork/patch `jupyter-jvm-basekernel` 2.4.0), move the statement executor
to virtual threads with real cancellation, and add PR CI + coverage + dependency
verification before any feature work.

---

## 2. Verified Current Architecture

### 2.1 Process & protocol layer

```
jupyter (jupyter_client)
   │  ZMQ: shell / control / stdin / iopub / heartbeat
   ▼
jupyter-jvm-basekernel 2.3.0  (external dep, same author)
   ├─ JupyterConnection      — ZMQ context, one shared handler map [V]
   ├─ ShellChannel (shell + control)
   │    └─ Loop thread: poll(0) → handler.handle(msg) inline → sleep 50 ms [V]
   ├─ StdinChannel, IOPubChannel, HeartbeatChannel (each a Loop thread) [V]
   └─ BaseKernel
        ├─ becomeHandlerForConnection(): registers handlers for execute, inspect,
        │  complete, is_complete, history, kernel_info, shutdown, interrupt, comm [V]
        ├─ handleExecuteRequest() is `synchronized` [V]
        └─ replaceOutputStreams() per execute: System.out/err/in → JupyterIO,
           deferred restore after reply [V]
   ▼
io.github.spencerpark:ijava 1.4.5
   ├─ IJava.main() — reads ijava-kernel-metadata.json, binds connection, kernel.run() [V]
   ├─ JavaKernel extends BaseKernel — registers 13 magic classes [V]
   ├─ CodeEvaluator / CodeEvaluatorBuilder — JShell engine (lazy-init on first eval) [V]
   ├─ IJavaExecutionControl (ExecutionControl SPI) — cached thread pool + timeout [V]
   └─ magics/ — 13 magic classes, ~5,300 LOC [V]
```

Key facts (all [V]):

- **Single handler map shared by shell and control channels** (`JupyterConnection.handlers`):
  execute/complete/inspect/is_complete/history arrive on the *shell* channel loop thread;
  interrupt arrives on the *control* channel loop thread — so interrupt stays responsive
  while a cell runs, but **completions and hover queue behind the running cell**.
- **Loop sleeps 50 ms after every iteration** (`SHELL_DEFAULT_LOOP_SLEEP_MS = 50`,
  `Loop.run()` sleeps unconditionally when `sleep > 0`): every message on an idle kernel
  pays up to ~50 ms fixed latency; the sleep also runs after handling each message.
- **`handleExecuteRequest` is `synchronized`** on the kernel instance: cell execution is
  serialized even though the loop could dispatch concurrently.
- **stdout capture** is a `PrintStream(jupyterOut, true)` (autoFlush → line-streamed to
  iopub) swapped in only during `handleExecuteRequest` and restored afterwards
  (`replaceOutputStreams`). Correct for the serialized single-cell flow; thread-unsafe by
  design for anything else.
- **HMAC message signing** is implemented in basekernel (`HMACGenerator`) — protocol
  auth is fine.
- **`kernel_info`/banner metadata** comes from `KERNEL_META` = `kernel-metadata.json`.
  The fat jar contains the *basekernel dependency's* `kernel-metadata.json`
  (`{"version":"2.3.0","project":"jupyter-jvm-basekernel"}`), so the banner's
  "implementation by ..." line misreports the implementation.

### 2.2 Execution engine (IJava-specific)

- `JavaKernel` constructor builds the JShell engine eagerly
  (`CodeEvaluatorBuilder.build()` → `JShell.builder().executionControl(...).build()`,
  `CodeEvaluatorBuilder.java:206-214`) but **startup scripts are evaluated lazily on the
  first `eval()`** (`CodeEvaluator.java:128-135`) — deliberately, to surface errors on the
  first cell. Cost: the first cell pays the `ijava-jshell-init.jshell` + `print.jshell`
  evaluation on top of a warm JVM.
- `eval(String, boolean)` path (`JavaKernel.java:316-343`):
  - regex-based `%` magic transformation (`magicsTransformer`, 3 regexes, [V]);
  - `COMMENT_PATTERNS` strip applied per eval ([V]);
  - for `evalWithPrint`: **O(n) scan over every stored snippet** via `skip`/`reduce` +
    `String.replaceAll` to append `printf` to the last non-blank line, and a linear
    `lastIndexOf` to find the statement (`JavaKernel.java:327-340`) — degrades as the
    session grows;
  - `jshell.eval` then runs the statement.
- `complete(String)` (`JavaKernel.java:397-414`) calls `jshell.sourceCodeAnalysis`
  (completion + import completion) **on the shell loop thread, per keystroke request**.
- `inspect(String)` (`JavaKernel.java:345-395`) calls `jshell.sourceCodeAnalysis`
  (documentation) per hover, same thread.
- `interrupt()` (`JavaKernel.java:431-434`) delegates to
  `IJavaExecutionControl.interrupt()` → `runningTasks` futures cancelled.

### 2.3 Statement execution control

`IJavaExecutionControl` (`IJavaExecutionControl.java`):

- `newCachedThreadPool` with **non-daemon** threads (`:76`) — unbounded, one per
  concurrent statement;
- `execute()` submits the statement to the pool and does
  `future.get(timeout, unit)`; on `TimeoutException` it logs "Timed out" and returns —
  **it never cancels the task**, so the user's code keeps running on a leaked thread,
  holding its classloaders/allocations, and the next cell may interleave with it
  (`:94-138`);
- `DEFAULT_TIMEOUT` is 60 s (changed on this branch from 120 s,
  `IJavaExecutionControlProvider.java:55-56,73-74`), overridable via `IJAVA_TIMEOUT` /
  `NO_TIMEOUT=-1` through `install.py` env mapping.

### 2.4 Compile/resolve magics

- `RuntimeCompiler` (used by the compile path): writes `.java` **relative to the CWD**
  (`:68-70`), then per compile creates a **new `URLClassLoader` and closes it in
  try-with-resources** (`:100-103`) — every `%compile`/`mycompile` cycle produces fresh
  type identities (old instances keep the old loader alive), and the "already exists"
  check consults a loader that no longer holds the classes.
- `JavaCompilerMagics` (the `compile` magic with a `CompilationContext`) is better
  behaved: fixed workspace `~/.jupyter/java-workspace` (`:28`), explicit
  `StandardLocation.CLASS_OUTPUT` (`:99-100`), writes sources under
  `workspace/sources/<package>` (`:140-163`) — but still one `JavaCompiler` task per call.
- `MavenResolver`: **hardcoded remote repositories** — central, repo1, sonatype releases
  (`:61-69`); no offline mode, no proxy config, no user-specified mirror; `resolve(pomPath)`
  reads an arbitrary local POM and resolves its dependency tree over the network.
- `MagicsTool.cmd`: `Runtime.getRuntime().exec(args)` (deprecated), **reads stdout fully
  before stderr** (`:91-100`) — classic pipe-buffer deadlock if the child writes >64 KB to
  stderr before finishing stdout; no timeout, exit code ignored.
- `ShellMagics` (`sh`): static cached thread pool; `SingleShellMagics` (`!`): a
  `Process` + fixed pool of 2 with `StreamGobbler`s and a `Thread.sleep(100)` polling
  loop for completion — works, but the sleep adds up to 100 ms latency per external
  command.
- `GitMermaidMagics`: `ProcessBuilder` around `git` (diagram from git history), no timeout.

### 2.5 Build, packaging, CI

- `build.gradle` [V]:
  - toolchain **Java 21** (`:14, :83`); wrapper **Gradle 8.5**;
  - deps: `jupyter-jvm-basekernel 2.3.0`, `gson 2.10.1`, maven-resolver
    `provider 3.8.6` / core `1.8.2`, `logback 1.5.7`, JUnit **4.13.2**,
    javaparser `3.25.8`, plantuml `1.2026.0`, classgraph `4.8.168`, lombok `1.18.30`;
  - Shadow 8.1.1 fat jar; **no `processResources` token filtering anywhere** (grep for
    `filesMatching|expand` → none), so `ijava-kernel-metadata.json` ships with literal
    `@version@`/`@project@` — **confirmed present in `build/libs/IJava-all.jar`**;
  - `shadowJar` also copies `build.gradle` into the jar (`:87-104`, odd artifact);
  - `packDist` zips the jar + kernel dir + `install.py` for jupyter install;
  - `build.finalizedBy packDist`.
- `gradle.properties` [V]: daemon disabled; **dependency verification is present but
  commented out**.
- `.github/workflows/build-release.yml` [V]: triggers **only on tag push / manual
  dispatch**, runs on a **self-hosted** Linux runner, sets up Temurin 21, builds, then a
  smoke test that installs a fresh venv + jupyter and **hand-writes a minimal
  `kernel.json`** (i.e., the real `install.py`/`kernel.json` template path is not
  exercised). No PR/push CI, no JDK matrix, no coverage, no lint.
- `src/main/resources/kernel.json` [V]: `argv: java -jar ...-all.jar`,
  `interrupt_mode: "message"`, empty `env` (no `-Xmx` or other JVM tuning surface).
- Logging [V]: `logback.xml` = console, root `INFO`; `IJava.main` forces
  `JUPYTER_LOGGER` to WARNING (`IJava.java:97`) — kernel diagnostics are effectively
  silenced by default; there is a stray `System.out.printf("found startup file: %s%n",
  path)` in `CodeEvaluatorBuilder.java:185` that can leak to the real terminal before
  basekernel swaps the streams; several commented-out debug blocks remain
  (`JavaKernel.java:92-99`, `CodeEvaluatorBuilder.java:150-157`).
- Tests [V]: 8 test classes, ~582 lines, JUnit 4; includes a good DX guard
  (`DuplicateMagicsTest` scans class files for duplicate `@CellMagic`/`@LineMagic` names)
  and one end-to-end-ish DBMS/PlantUML test that requires Graphviz on PATH (environmental
  failure seen before `dot` was installed). No protocol-level integration test, no
  coverage tooling, no JMH.
- Repo state [V]: stray `.git` directory no longer present; `notebooks/out` and
  `build copy.gradle` removed (staged on this branch); license `${author}` → `ebpro`
  sweep done (45 files modified at audit time, uncommitted).

---

## 3. Deep Audit Findings

Severity: **P0** = fix now (correctness/reliability/security, low effort) ·
**P1** = high impact, medium effort · **P2** = strategic.

| # | Sev | Area | Finding (evidence) | Risk / impact |
|---|-----|------|--------------------|---------------|
| F1 | P0 | Packaging | `ijava-kernel-metadata.json` ships with literal `@version@`/`@project@` (verified in `IJava-all.jar`); no resource filtering in `build.gradle`; jar's `kernel-metadata.json` is basekernel's, so banner/kernel_info misreport implementation | Wrong version in `kernel_info`, banner, release notes automation; confuses debugging |
| F2 | P0 | Reliability | `IJavaExecutionControl.execute` does not cancel the task on `TimeoutException` (`:129-134`); timed-out statements keep running on leaked non-daemon threads from an unbounded cached pool (`:76`) | Resource exhaustion; interleaved execution of "dead" cells; JVM never exits cleanly |
| F3 | P0 | CI/quality | No PR/push CI; only tag-triggered self-hosted build; smoke test bypasses `install.py` (hand-written kernel.json); single JDK (21) | Regressions merge untested; packaging path unverified; no 25 validation |
| F4 | P0 | Security | No dependency verification (commented out in `gradle.properties`); no dependency vulnerability scanning; supply-chain exposure in a fat jar that runs arbitrary user code | Tampered/stale deps reach every student machine |
| F5 | P0 | Reliability | `MagicsTool.cmd` reads stdout before stderr with no concurrency (`:91-100`) → pipe deadlock on >64 KB stderr; no timeout; deprecated `Runtime.exec` | Kernel can hang indefinitely on `cmd` |
| F6 | P1 | Performance | All message types share one handler map; complete/inspect/is_complete queue behind running cells; loop sleeps 50 ms after every iteration (basekernel `ShellChannel`/`Loop`) | Completion/hover frozen while a cell runs; ~50 ms fixed latency on idle kernel |
| F7 | P1 | Performance | `evalWithPrint` does O(n) snippet scan + `replaceAll` per eval (`JavaKernel.java:327-340`) | Slowdown grows linearly with session length; GC pressure from per-eval regex work |
| F8 | P1 | Performance | First cell pays startup-script evaluation (lazy `CodeEvaluator.init`, `:128-135`) | First-cell latency 2–3× warm cells (measured in practice; mechanism verified) |
| F9 | P1 | Reliability | `RuntimeCompiler` writes sources to CWD and creates/closes a new `URLClassLoader` per compile (`:68-70`, `:100-103`) | CWD pollution; type-identity churn across compiles; stale "already exists" check |
| F10 | P1 | Security | `MavenResolver` hardcoded remotes, no offline/proxy/mirror (`:61-69`); network resolution by default from notebook cells | Unexpected egress; breaks in air-gapped classrooms; no policy |
| F11 | P1 | Quality | JUnit 4; ~580 test lines; no coverage gate; no lint/static analysis; no JMH | Weak safety net for the P1/P2 refactors |
| F12 | P1 | DX/Observability | `JUPYTER_LOGGER` forced to WARNING (`IJava.java:97`); console-only logging; stray `System.out` debug (`CodeEvaluatorBuilder.java:185`); commented debug blocks | Hard to diagnose kernel issues; terminal noise |
| F13 | P1 | Packaging | `kernel.json` has empty `env` — no JVM memory/flag surface; `shadowJar` bundles `build.gradle` into the artifact | OOM on big datasets with no recourse; noise in artifact |
| F14 | P2 | Architecture | Hard dependency on 2019-era `jupyter-jvm-basekernel` (ZMQ binding, single handler map, 50 ms loop) blocks F6-class fixes without forking it | Ceiling on performance/protocol work |
| F15 | P2 | Architecture | No engine abstraction: JShell internals are woven through `JavaKernel` (eval/complete/inspect/print paths) | Can't swap to an incremental (JDT) engine or AOT-compiled path |
| F16 | P2 | Security | No isolation story: user code shares the kernel JVM; no read-only mode, no network toggle, no memory cap, no process-level timeout kill | Unsuitable for multi-tenant/graded environments |
| F17 | P2 | Performance | JShell per-statement recompilation; no persistent incremental compile; snippets accumulate without reset policy | Long sessions degrade (compile time, memory) |
| F18 | P2 | Rich output | Output is text/HTML via magics only; no table/dataframe API, no `update_display_data` usage, no plot bridge | Below 2026 notebook UX expectations |
| F19 | P1 | Maintainability | Duplicated magic infrastructure across 13 classes (~5.3k LOC), stringly-typed arg parsing, mixed `List<String>`/map APIs | Slow to extend; error-prone (see `DuplicateMagicsTest` existing to catch name collisions) |
| F20 | P2 | DX | Install path is zip + `install.py` into jupyter's data dir; no pip-packaged kernelspec; no `ijava --doctor`/preflight | Friction for students; env issues (like the Graphviz one) surface late |

---

## 4. SOTA 2026 Target Architecture

```
                        ┌────────────────────────────────────────────┐
 jupyter_client 8.x  ──▶│  kernel process (java 25, -Xmx tunable)    │
  (ZMQ, protocol 5.3)   │  ┌──────────────────────────────────────┐  │
                        │  │ ijava-protocol (basekernel 2.4.x or  │  │
                        │  │ fork)                                 │  │
                        │  │  • execute: dedicated worker, serial  │  │
                        │  │  • complete/inspect/is_complete:      │  │
                        │  │    bounded parallel pool (no 50 ms    │  │
                        │  │    sleep; poll(0) only)               │  │
                        │  │  • interrupt on control channel       │  │
                        │  └──────────────┬───────────────────────┘  │
                        │                 ▼                          │
                        │  ┌──────────────────────────────────────┐  │
                        │  │ IJavaKernel (this repo)               │  │
                        │  │  • Engine SPI ── JShellEngine (def)   │  │
                        │  │                 JdtEngine (P2)        │  │
                        │  │  • StatementRunner: virtual threads + │  │
                        │  │    cancellation + wall-clock timeout  │  │
                        │  │  • Session state: snippet registry,   │  │
                        │  │    incremental classpath, metrics     │  │
                        │  │  • Magics: unified registry+parser    │  │
                        │  └──────────────┬───────────────────────┘  │
                        │                 ▼ (opt-in, IJAVA_SANDBOX)  │
                        │  child-JVM execution mode: no network,     │
                        │  read-only FS, hard kill on timeout        │
                        └────────────────────────────────────────────┘
```

Design decisions (with rationale):

1. **Keep JShell as the default engine.** JShell gives snippet semantics,
   completion, hover, and import management that a hand-rolled Compiler-API REPL would
   have to reimplement for years of teaching use. SOTA in 2026 for an *interactive
   teaching kernel* is not "fastest compile" — it is **responsive completion while
   executing, bounded memory, and predictable first-cell latency**. [I]
2. **Engine SPI (`IJavaEngine`)** with `eval / complete / inspect / reset / metrics`.
   JShell engine now; JDT-based incremental engine later (JDT provides true incremental
   compilation and binding-based completion/hover that beat JShell's textual analysis).
   The SPI is the only change to `JavaKernel`'s structure; magics stay unchanged. [I]
3. **Statement execution on virtual threads** (Java 21+, LTS 25):
   `Executors.newVirtualThreadPerTaskExecutor()`, `future.cancel(true)` on timeout,
   `ScopedValue` for the current-cell context (working dir, classpath, workspace).
   Virtual threads make the "one thread per statement" model free; cancellation +
   optional child-JVM mode make timeout *enforceable*. [I]
4. **Decoupled message loop.** Fork `jupyter-jvm-basekernel` (same author, small codebase)
   as `ebpro/jupyter-jvm-basekernel` 2.4.0: (a) separate bounded executor for
   complete/inspect/is_complete/history so they never queue behind execute; (b)
   configurable loop sleep (default 0 with `poll(timeout)`); (c) keep `synchronized`
   execute for state safety. Fallback if upstreaming is impossible: accept the
   serialization and only fix the sleep. [I]
5. **Security defaults, not sandbox-by-default.** 2026 SOTA for Jupyter kernels is
   layered: in-JVM kernel by default (fast, simple) + **opt-in strict mode**
   (`IJAVA_SANDBOX=process`): each statement runs in a child JVM with network disabled
   (custom `URLStreamHandlerFactory` + no DNS), read-only user FS, and hard-kill on
   timeout — plus documented container-level sandboxing (JupyterHub profiles) for
   multi-tenant use. `SecurityManager` is explicitly *out* (deprecated for removal).
   [I]
6. **Packaging done properly**: `processResources` filtering for real versions; a pip
   package `ijava-kernel` that installs the kernelspec + jar (keeps `install.py` for
   compat); `kernel.json` exposes `IJAVA_JAVA_OPTS` env; fat jar no longer bundles
   `build.gradle`. [I]
7. **Observability by default**: structured JSON logs (logback JSON encoder) to the
   terminal log file (not the notebook), per-cell metrics (startup, startup-script,
   compile, eval, complete p50/p95) to stderr; optional OTel exporter behind a
   separate Gradle module so the fat jar stays slim. `JUPYTER_LOGGER` silencing
   removed in favor of `IJAVA_LOG_LEVEL`. [I]
8. **Java 25 primary, Java 21 floor.** Build toolchain 25 with `--release 21` so the
   published kernel still runs on 21 LTS JVMs (what many distros ship); CI matrix
   tests both. Virtual-thread *executor* usage stays `--release`-safe (Java 21+ API).
   [I] (user has confirmed Java 25 is acceptable; `25.0.4-tem` installed via sdkman.)

---

## 5. Prioritized Implementation Plan

### P0 — quick wins (target: ≤ 1 day total, 4–6 small PRs)

| ID | Task | Files | Effort | Risk | Validation |
|----|------|--------|--------|------|------------|
| P0-1 | **Fix version placeholders**: add `processResources { filesMatching('ijava-kernel-metadata.json') { expand project: rootProject.name, version: version } }`; also emit a correct `kernel-metadata.json` (project=`ijava`) into the jar so banner/kernel_info report IJava, not basekernel | `build.gradle`, `src/main/resources/` | 0.5 h | low | `unzip -p build/libs/IJava-all.jar ijava-kernel-metadata.json` shows real version; `jupyter kernelspec` smoke shows `ijava 1.4.x` |
| P0-2 | **Timeout actually cancels**: in the `TimeoutException` branch call `future.cancel(true)` (and track cancelled tasks so `interrupt()` doesn't double-report); make the pool daemon + bounded (e.g. 2) or virtual threads; add `IJAVA_MAX_CONCURRENT_STATEMENTS` | `IJavaExecutionControl.java`, `IJavaExecutionControlProvider.java` | 1 h | low | new unit test: submit sleeping statement with 1 s timeout → task thread count returns to 0; kernel exits after shutdown |
| P0-3 | **PR CI**: new `.github/workflows/ci.yml` — `pull_request` + `push` to main branches; `ubuntu-latest`; matrix JDK 21/25 (temurin); `./gradlew build` (runs tests) + `./gradlew shadowJar` + jar-content assertion script (version placeholder check from P0-1); keep tag workflow for releases | `.github/workflows/` | 1 h | low | PR check goes red on a deliberate regression |
| P0-4 | **Supply chain**: re-enable Gradle dependency verification (`gradle/verification-metadata.xml` generated once), add OWASP `dependency-check-gradle` to CI (fail on critical/high, report-only first release) | `gradle.properties`, `gradle/`, CI | 1 h | low | `./gradlew build` verifies checksums; CI report artifact |
| P0-5 | **`cmd` magic deadlock + hygiene**: read stdout/stderr concurrently (reuse `StreamGobbler` pattern from `SingleShellMagics`), add `timeout=` arg (default 30 s), replace `Runtime.exec` with `ProcessBuilder`, return/echo exit code; remove stray `System.out` in `CodeEvaluatorBuilder.java:185` and commented debug blocks | `MagicsTool.java`, `CodeEvaluatorBuilder.java`, `JavaKernel.java` | 1 h | low | test: `cmd "sh -c 'head -c 200000 /dev/zero \| tr \\0 e; sleep 0.1'"` style large-stderr case doesn't hang; timeout honored |
| P0-6 | **JVM surface in kernel.json**: add `env: { "IJAVA_JAVA_OPTS": "" }` consumed by `IJava.main` (split into `-Xmx` etc. before engine start) — or document `JAVA_TOOL_OPTIONS`; stop bundling `build.gradle` into the fat jar | `kernel.json`, `IJava.java`, `build.gradle` | 0.5 h | low | kernelspec smoke with `-Xmx256m` visible in process args |

### P1 — high impact (2–4 weeks, ordered)

| ID | Task | Files / scope | Effort | Risk | Validation |
|----|------|---------------|--------|------|------------|
| P1-1 | **Decouple message loop**: fork `jupyter-jvm-basekernel` → `ebpro` 2.4.0 with (a) complete/inspect/is_complete/history on a bounded pool (4) separate from execute, (b) `poll(timeout)` with configurable sleep (default 0), (c) keep execute serialized. Bump dep in IJava | fork + `build.gradle` | 3–5 d | medium | new protocol integration test: start 60 s cell, assert complete requests answered < 250 ms p95; idle is_complete latency < 10 ms p50 (vs ~50 ms today) |
| P1-2 | **Virtual-thread `StatementRunner`** (replaces cached pool): virtual threads, `cancel(true)` on timeout, `ScopedValue` cell context, metrics per statement; JShell engine untouched | new `execution/StatementRunner.java`, `IJavaExecutionControl` | 2 d | medium | soak test: 500 timed-out cells → no thread growth, RSS flat; cancel latency < 50 ms for cooperative code |
| P1-3 | **First-cell latency**: evaluate startup scripts eagerly at engine init, capture failure, and surface it on the first cell (keeps today's error UX, moves the cost out of the first user cell); keep `IJAVA_STARTUP_SCRIPT` override | `CodeEvaluator.java`, `CodeEvaluatorBuilder.java` | 1 d | low | benchmark: first `println` cell < 800 ms on warm JVM (measure before/after in CI bench job) |
| P1-4 | **Session state cleanup for `evalWithPrint`**: replace O(n) snippet scan with a last-statement registry (store the last eval string + id in `JavaKernel`, no `skip`/`reduce` over snippets) | `JavaKernel.java` | 1 d | low | micro-benchmark: 10k snippets, print-eval constant time; existing tests green |
| P1-5 | **Compile magic consolidation**: one workspace root (`IJAVA_WORKSPACE`, default `~/.jupyter/java-workspace`), one persistent classloader registry per package, no CWD writes, no per-compile loader close; deprecate `RuntimeCompiler` in favor of `JavaCompilerMagics` internals | `RuntimeCompiler.java`, `JavaCompilerMagics.java`, `CompilerMagics.java` | 3 d | medium | test: compile A → instance → recompile A → new code runs on existing call sites (type identity policy documented); no files in CWD |
| P1-6 | **Maven resolver hardening**: offline mode (`IJAVA_OFFLINE=1` / `%resolve --offline`), configurable mirrors (`IJAVA_MAVEN_REPOS`), proxy via standard JVM props, timeout on resolution, lockfile-style cache in workspace | `MavenResolver.java` | 2 d | medium | offline test with primed cache; network test in CI |
| P1-7 | **Test & quality baseline**: migrate to JUnit 5 (+`junit-platform`), add JaCoCo with ratcheting gate (start: 30 % line coverage on `execution` + `magics` packages), Spotless (google-java-format) + ErrorProne on CI, keep `DuplicateMagicsTest` | `build.gradle`, `src/test/` | 3 d | low | CI gate; coverage report artifact; ratchet config in `build.gradle` |
| P1-8 | **Observability**: logback JSON encoder to stderr (notebook output untouched), per-cell timing metrics (startup / startup-script / compile / eval / complete), `IJAVA_LOG_LEVEL` replaces the hardcoded WARNING silencing | `logback.xml`, `IJava.java`, `JavaKernel.java` | 2 d | low | log fixture tests; metrics visible in `jupyter` terminal log |
| P1-9 | **Dependency refresh**: gson 2.13.x, maven-resolver 1.9.x/3.9.x, javaparser 3.26.x, classgraph latest, logback latest 1.5.x, basekernel 2.4.0 (P1-1); re-run verification metadata (P0-4) | `build.gradle` | 1–2 d | low | build + full test suite on 21 & 25 |

### P2 — strategic (quarter+)

| ID | Task | Rationale / trade-offs | Effort | Validation |
|----|------|------------------------|--------|------------|
| P2-1 | **`IJavaEngine` SPI + JDT engine** | SPI (1 wk) is cheap insurance; JDT incremental engine (6–10 wk) delivers true incremental compile + binding-based completion/hover (faster & more accurate than JShell textual analysis). Trade-off: big effort, two engines to maintain; mitigate by making JShell the only default and JDT opt-in (`IJAVA_ENGINE=jdt`) | 1 wk + 6–10 wk | parity test suite (both engines run the same magic/cell corpus with golden outputs); JDT engine: 50-line re-declaration recompile < 300 ms; completion p50 < 60 ms on 1k-line session |
| P2-2 | **Strict-mode child-JVM execution** (`IJAVA_SANDBOX=process`) | Real timeout enforcement + network/FS isolation without `SecurityManager`. Trade-off: per-statement JVM cost (~200–400 ms) and serialization of state across statements → keep in-JVM default; strict mode targets graded/multi-tenant environments. Pair with documented container sandboxing (JupyterHub profiles) | 3–4 wk | security test suite: `Runtime.exec("curl ...")` fails in strict mode; 60 s infinite loop killed at 60 s with no zombie threads |
| P2-3 | **Rich output APIs** | table/dataframe magic (`%table` → styled HTML + CSV download), `update_display_data` for in-place updates (basekernel already publishes it), plot bridge (plotly JSON) | 3–4 wk | notebook golden-output tests (nbconvert to HTML, diff) |
| P2-4 | **Memory governance** | `%reset` exists via jshell; add auto-reset policy (`IJAVA_RESET_AFTER_N_CELLS`), snippet/heap metrics in status bar, classloader registry pruning for compile magic | 2 wk | soak: 10k cells, RSS growth < 100 MB; `%mem` magic report |
| P2-5 | **pip-packaged kernelspec** (`ijava-kernel` on PyPI) + `ijava doctor` preflight (java version, workspace, Graphviz for dbms magic, network for maven) | 2026 install SOTA is `pip install ijava-kernel && jupyter kernelspec install ...`; `doctor` would have caught the Graphviz env failure seen in this repo | 2 wk | fresh-container install test in CI (ubuntu + venv + `pip install`) |
| P2-6 | **Protocol benchmark suite in CI** | Java-side JMH for hot paths (magic transform, error styling, classpath globs) + Python `jupyter_client` harness for end-to-end latencies (startup, first cell, warm cell, complete p95); store baselines, fail on > 15 % regression | 2 wk | bench job with baseline artifacts; PR comment on deltas |
| P2-7 | **OpenTelemetry module** (optional) | Traces per message type, metrics for cells; separate Gradle subproject to keep fat jar slim; off by default | 1–2 wk | golden OTLP fixture test |

---

## 6. Performance Targets

Reference machine: 4 vCPU / 8 GB CI runner (ubuntu-latest), JVM 25, warm page cache.
All targets are **p50 unless noted**; measured by the P2-6 harness, asserted in CI
starting at P1-1 (latency) and P1-3 (first cell).

| Metric | Today (measured/mechanism) | 2026 target |
|--------|---------------------------|-------------|
| Kernel start → `kernel_info` reply | ~1–2 s (JVM + ZMQ + JShell build) [I] | **< 1.2 s p50**, < 1.8 s p95 |
| First cell (`System.out.println`) | warm-cell + startup-script eval (lazy init) [V mechanism] | **< 800 ms p50** (after P1-3) |
| Warm cell (println) | ~50–150 ms (loop sleep + jshell) [I] | **< 100 ms p50**, < 200 ms p95 |
| Completion (idle kernel) | up to ~50 ms loop sleep + `sourceCodeAnalysis` [V mechanism] | **< 60 ms p50**, < 150 ms p95 (P1-1) |
| Completion (while cell executing) | **blocked until cell ends** [V] | **< 250 ms p95** (P1-1) |
| Hover/inspect | same as completion [V] | **< 200 ms p50** |
| Recompile 50-line declaration (JShell default) | ~300–800 ms [I] | **< 500 ms p50** (JShell); < 300 ms with JDT engine (P2-1) |
| `%compile` (persistent workspace) | new classloader + CWD writes [V] | **< 700 ms p50**, zero CWD writes (P1-5) |
| Interrupt → stop | future cancel (cooperative) [V] | cancel signal < 50 ms; strict mode hard-kill at timeout (P2-2) |
| Memory, idle after 10k cells | unbounded snippet growth [I] | **< 100 MB growth** (P2-4) |
| `./gradlew test` wall time | ~30–60 s local [I] | **< 90 s** on 4 vCPU CI (gate) |

---

## 7. Validation Plan

Layered, all runnable in CI (P0-3 adds the runner):

1. **Unit (JUnit 5)** — every P0/P1 item ships with unit tests:
   - P0-2: timeout cancellation (thread count returns to baseline), bounded concurrency;
   - P0-5: large-stderr `cmd` no-hang, timeout honored, exit code surfaced;
   - P1-4: `evalWithPrint` constant-time over 10k snippets;
   - P1-5: type-identity policy + workspace isolation;
   - P1-6: offline resolution, mirror config.
2. **Protocol integration test** (new): Java-side ZMQ client (or Python `jupyter_client`
   step in CI) that starts the real jar: `kernel_info` → asserts version == build
   version (catches F1 class bugs) → run 60 s cell → assert `complete` answers < 250 ms
   p95 while busy → `interrupt` → assert kernel responsive within 1 s. Runs on every PR
   (JDK 21 & 25).
3. **Packaging smoke** (existing, extended): the tag-release smoke must install via the
   **real** `install.py`/`kernel.json` (replace the hand-written kernel.json) and run
   `magics_demo.ipynb` through `jupyter nbconvert --execute`; add a fresh-venv +
   `pip install ijava-kernel` path once P2-5 lands.
4. **Benchmark gates** (P2-6): JMH (magic transform, error styler, classpath globs) +
   protocol harness baselines in CI artifacts; fail on > 15 % regression on tracked
   metrics; baselines updated via explicit PR.
5. **Quality gates**: Spotless + ErrorProne (P1-7), JaCoCo ratchet (start 30 % on
   `execution` + `magics`, +5 % per release), dependency verification + OWASP
   dependency-check (P0-4).
6. **Compatibility matrix**: JDK 21 (floor, `--release 21` artifact) and 25 (primary);
   Jupyter 7.x / jupyter_client 8.x; Python 3.11–3.13 in the smoke venv.
7. **Soak test** (nightly, not PR): 1k warm cells + 500 timed-out cells + 200
   compile cycles → assert thread count, RSS growth, and interrupt latency bounds.
8. **Security test suite** (grows with P2-2): egress blocked in strict mode, FS read-only
   enforcement, timeout hard-kill, no secrets in logs (grep-based log fixture test).

---

## 8. Migration Plan

All work lands as small branches off `feature/update2026` (which carries the completed
hygiene/timeout/docs work) → merged to `master` in the order below. Each step is
releasable and CI-green on its own. No step requires a big-bang migration; users on
`1.4.x` keep working (protocol unchanged, kernel name unchanged).

| Step | Branch | Contents | Release |
|------|--------|----------|---------|
| 1 | `chore/build-correctness` | P0-1, P0-6, P0-5 (hygiene half) | v1.4.6 (patch) |
| 2 | `fix/timeout-cancellation` | P0-2 | v1.4.7 (patch) — behavior fix, call out in release notes ("timeouts now actually stop leaking threads") |
| 3 | `ci/pr-matrix` | P0-3, P0-4 | no user release (infra) |
| 4 | `deps/refresh-2026` | P1-9 + P1-7 (JUnit 5, JaCoCo, Spotless/ErrorProne) | v1.5.0-rc1 |
| 5 | `feat/basekernel-2.4` | P1-1 (fork + release `ebpro/jupyter-jvm-basekernel` 2.4.0), then dep bump | v1.5.0-rc2 |
| 6 | `perf/statement-runner` | P1-2, P1-3, P1-4 | v1.5.0 |
| 7 | `feat/workspace-and-resolver` | P1-5, P1-6, P1-8 | v1.5.1 |
| 8 | `feat/engine-spi` | P2-1 phase 1 (SPI + JShell engine extraction only) | v1.6.0 (API-additive) |
| 9 | `feat/strict-sandbox` | P2-2 + P2-5 (pip kernelspec + doctor) | v2.0.0-rc1 |
| 10 | `feat/jdt-engine`, `feat/rich-output`, `feat/otel` | P2-1 phase 2, P2-3, P2-7 | v2.0.0 |
| 11 | continuous | P2-4, P2-6 soak/bench gates harden over releases | — |

Compatibility & rollback notes:

- **Protocol**: stays Jupyter messaging 5.3 over ZMQ; no notebook-side changes needed.
  Rollback of any release = reinstall previous kernelspec (`jupyter kernelspec install
  ijava-<ver>.zip --user`).
- **Engine**: JShell remains default through v2.x; JDT engine opt-in only; SPI is
  additive so v1.6 consumers (custom magics/extensions) are unaffected.
- **Timeout semantics**: v1.4.7 changes a leak into a cancellation — code that
  *depended* on timed-out cells continuing to run in the background (rare; anti-pattern)
  is the only behavior break; document in release notes.
- **Java floor**: published artifact keeps `--release 21`; Java 25 is the development
  and primary-tested runtime. Users on Java 17 are EOL (documented requirement: 21+).
- **Data**: `~/.jupyter/java-workspace` layout is preserved (P1-5 only adds the
  `IJAVA_WORKSPACE` override); no migration of user files.
- **CI**: tag workflow untouched until P0-3 lands; after that, tag builds run on top of
  the green PR pipeline (same runner image), so release risk drops, not rises.
- **Known environmental dependency**: `JavaDBMSMagics` requires Graphviz `dot` on PATH
  (verified failure mode in this repo before `dot` was installed); `ijava doctor` (P2-5)
  reports it preflight; until then, CI smoke includes `apt-get install graphviz`.

---

## Appendix A — Files inspected (audit evidence base)

- `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`
- `.github/workflows/build-release.yml`
- `README.md`, `docs/magics.md`, `UPGRADE.md`
- `src/main/java/io/github/spencerpark/ijava/`: `IJava.java`, `JavaKernel.java`
- `.../execution/`: `CodeEvaluator.java`, `CodeEvaluatorBuilder.java`, `IJavaExecutionControl.java`, `IJavaExecutionControlProvider.java`
- `.../magics/`: `MagicsTool.java`, `MavenResolver.java`, `JavaCompilerMagics.java`, `JavaMagics.java`, `ShellMagics.java`, `SingleShellMagics.java`, `TimeItMagics.java`, `GitMermaidMagics.java` (+ registration in `JavaKernel.java:115-137`)
- `.../utils/RuntimeCompiler.java`
- `src/main/resources/`: `kernel.json`, `install.py`, `ijava-kernel-metadata.json`, `logback.xml`, `ijava-jshell-init.jshell`, `print.jshell`
- `src/test/java/io/github/spencerpark/ijava/magics/DuplicateMagicsTest.java` (+ 7 other test classes)
- Artifacts: `build/resources/main/ijava-kernel-metadata.json`, `build/libs/IJava-all.jar` (unzip-verified)
- `jupyter-jvm-basekernel-2.3.0-sources.jar` (Gradle cache) → extracted to
  `/tmp/opencode/basekernel`: `BaseKernel.java`, `JupyterConnection.java`,
  `ShellChannel.java`, `Loop.java`, `JupyterIO.java`

## Appendix B — Verified vs inferred

- **Verified**: everything tagged [V] in §2/§3, plus jar contents, CI trigger, loop
  sleep, synchronized execute, handler-map sharing, placeholder version, hardcoded Maven
  remotes, `cmd` read order, JUnit 4 + test count, 50 ms constant, 60 s default timeout
  (this branch).
- **Inferred** [I]: all target numbers in §6 (to be measured by the P2-6 harness),
  effort estimates, JDT-engine performance expectations, strict-mode overhead, and the
  SOTA architectural choices in §4 (each with stated rationale/trade-off).
