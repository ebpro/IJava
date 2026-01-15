# IJava Magics — Comprehensive Audit & UX Improvement Plan
**Date:** January 15, 2026  
**Status:** Post-initial-refactor assessment

---

## Executive Summary

This document provides a deep audit of all magics in `src/main/java/.../magics/` and proposes a comprehensive plan to achieve best-in-class UX for Java Jupyter kernel users. The audit covers:

1. **Feature Coverage** — what's available and what's missing
2. **Consistency** — naming, argument parsing, error handling, output formatting
3. **Duplication** — overlapping responsibilities and refactoring opportunities
4. **Discoverability** — help text, documentation, examples
5. **Proposed Improvements** — actionable roadmap prioritized by user impact

**Key Findings:**
- ✅ Strong foundation: source inspection (JavaMagics), compilation (JavaCompilerMagics), PlantUML rendering, database schema/query magics
- ⚠️ Inconsistent option parsing, help text, and error messages across magics
- ⚠️ Duplication between `ShellMagics`, `SingleShellMagics`, `MyShellMagics`
- ⚠️ Missing: unified `--help` across all magics, comprehensive output control (JSON/CSV/HTML), interactive prompts for common workflows
- ⚠️ Opportunity: centralize utilities further, add magic "profiles" or presets for common tasks

---

## 1. Current State Inventory

### 1.1 Line Magics (Single-line utilities)

| Magic | Aliases | Purpose | Args/Options | Help Available? | Output Format | Notes |
|-------|---------|---------|--------------|-----------------|---------------|-------|
| `%classpath` | — | Add dirs/jars to classpath via glob | glob patterns | ❌ No | List of paths (return) | Works, no validation feedback |
| `%jars` | — | Add JAR files to classpath | glob patterns | ❌ No | List of JARs (return) | Works, no validation feedback |
| `%maven` | `addMavenDependency`, `addMavenDependencies` | Add Maven deps at runtime | `groupId:artifactId:version` | ❌ No | Logs to stdout | Powerful but no feedback on conflicts |
| `%pom`, `%loadFromPOM` | `loadFromPOM` | Load deps from pom.xml | path to pom.xml | ❌ No | Logs to stdout | Useful but no status summary |
| `%addMavenRepo`, `%mavenRepo` | `mavenRepo` | Add Maven repo URL | repo URL | ❌ No | Logs to stdout | Works, no validation |
| `%listMagic`, `%list` | `list` | List all magics | none | ❌ No | Stdout (formatted) | Good discoverability aid |
| `%listLineMagic` | — | List line magics | none | ❌ No | Stdout (formatted) | Good discoverability aid |
| `%listCellMagic` | — | List cell magics | none | ❌ No | Stdout (formatted) | Good discoverability aid |
| `%printWithName` | — | Toggle var name printing | `-h`/`--help` | ✅ Yes | Toggle message | Simple, works |
| `%printerPrefix` | — | Set printer prefix string | prefix string | ❌ No | Confirmation msg | Simple, works |
| `%cmd` | — | Run external shell command | command args | ❌ No | Stdout/stderr | Basic, no async support |
| `%load` | — | Load file contents into cell | file path | ✅ Partial (debug log) | String (return) | **Improved**: workspace-relative, quiet logger. Could add `--help`. |
| `%read` | — | Read file to string variable | file path | ✅ Yes (prints usage) | String (return) | Works; overlaps `%load` semantically |
| `%write` | — | Write variable to file | var_name, filename | ❌ No | Success message | Works, could validate write |
| `%commonshellcmd` | — | (Shell-related, exact role unclear from grep) | unknown | ❌ No | unknown | Needs investigation |

### 1.2 Cell Magics (Multi-line/block operations)

| Magic | Aliases | Purpose | Args/Options | Help Available? | Output Format | Notes |
|-------|---------|---------|--------------|-----------------|---------------|-------|
| `%%compile` | — | Compile Java class at runtime (JavaCompilerMagics) | `FQCN [-v] [-d] [-nowarn]` | ❌ No | Logs (stdout) + adds to classpath | **Core feature**: works well, verbose logging. Could add `--help`, structured output. |
| `%%mycompile` | — | Alternative compile magic (CompilerMagics) | `FQCN` | ❌ No | Logs (stdout) + adds to classpath | Overlaps `%%compile`. Decide: merge or deprecate one. |
| `%%write` | — | Write cell body to file | filename | ❌ No | Success message | Overlaps line `%write`. Consolidate? |
| `%%shell` | — | Run shell command (ShellMagics) | none | ❌ No | Stdout/stderr | Uses `zsh -c`. Basic; no option parsing. |
| `%%myshell` | — | Alternative shell (MyShellMagics, exact diff unclear) | unknown | ❌ No | Stdout/stderr | Duplication; audit reveals need to consolidate. |
| `%%commonshell` | — | Another shell variant | unknown | ❌ No | Stdout/stderr | **Duplication problem**: 3+ shell magics! |
| `%%timeIt`, `%%time`, `%%timeit` | `time`, `timeit` | Benchmark code execution | `epochs=N loops=M` | ✅ Yes (`-h`/`--help`) | LongSummaryStatistics (stdout) | Works well. Could add CSV/JSON output option. |
| `%%plantUML` | — | Render PlantUML diagram inline | `SVG`/`PNG`, `showSource`/`-s` | ❌ No | image/svg+xml or image/png | **Recently improved**: accepts `showSource` flag. Could add `--help`. |
| `%%plantUMLFile` | — | Render PlantUML from file(s) | `SVG`/`PNG` | ❌ No | image/svg+xml or image/png | Works; reads file list from body. Could add `--help`. |
| `%%javasrcMethodByAnnotationName` | — | Extract methods by annotation | `ClassName AnnotationName [index]` + options | ❌ No | Markdown (fenced) or plain | **New**: supports `--raw`/`--fenced`, `--src`. Needs `--help`. |
| `%%javasrcMethodByName` | — | Extract methods by name or regex | `ClassName methodName/regex [index]` + options | ✅ Yes (`--help`) | Markdown (fenced) or plain | **New**: full help text, regex, selection. Good reference for other magics. |
| `%%javasrcInterfaceByName` | — | Extract interface source | `FQCN` + options | ❌ No | Markdown (fenced) or plain | **New**: auto-resolve, supports `--src`. Needs `--help`. |
| `%%javasrcClassByName` | — | Extract class source | `FQCN` + options | ❌ No | Markdown (fenced) or plain | **New**: auto-resolve, supports `--src`. Needs `--help`. |
| `%%javasrcList` | — | List classes/methods in file (summary) | file path or FQCN | ❌ No | Markdown summary | **New**: useful discovery tool. Needs `--help`. |
| `%%rdbmsSchema` | — | Render database schema as PlantUML | schema_name + options | ❌ No | PlantUML (SVG/PNG) + optional source | **Improved**: supports `SVG`/`PNG`, `showSource`, `include=`, `exclude=`, `scale=`, `handwritten`. Needs `--help`. |
| `%%sqlAsTable` | — | Execute SQL and render as HTML table | SQL query | ❌ No | HTML table or CSV | **Improved**: supports `format=HTML/CSV`, `max=N`, `showQuery`. Needs `--help`. |
| `%%pom`, `%%loadFromPOM` | `loadFromPOM` | Load deps from pom.xml (cell variant) | path to pom.xml | ❌ No | Logs to stdout | Overlaps line magic. Consolidate? |

---

## 2. Consistency Analysis

### 2.1 Option Parsing

**Current State:**
- ✅ **JavaMagics** (javasrc*): centralized via `OptionUtils.parseOptions()` — supports `--raw`, `--fenced`, `--src=<dir>`, `key=value`, `selectIndex=N`
- ✅ **JavaDBMSMagics** (rdbmsSchema, sqlAsTable): manual parsing but consistent within class (supports `showSource`, `SVG`/`PNG`, `format=`, `max=`, `include=`, `exclude=`, `scale=`)
- ✅ **JavaPlantUMLMagics** (plantUML, plantUMLFile): manual parsing, accepts `SVG`/`PNG`, `showSource`/`-s`
- ⚠️ **TimeItMagics**: manual key=value parsing (`epochs=`, `loops=`)
- ⚠️ **MagicsTool** (%load, %read, %write): minimal or no option parsing
- ❌ **ClasspathMagics, CompilerMagics, ShellMagics**: no option parsing; positional args only

**Problems:**
- **Inconsistent flag syntax**: some use `--flag`, some `flag`, some `key=value`, some positional
- **No unified help pattern**: only `%%javasrcMethodByName` and `%%timeIt` have `--help`
- **Manual parsing duplication**: each magic reimplements similar logic

**Recommendation:**
- **Extend `OptionUtils`** to support:
  - Boolean flags (`--flag`, `-f`)
  - Key=value pairs (`key=value`)
  - Positional args (remaining after options)
  - Built-in `--help` / `-h` detection and short-circuit
- **Refactor all magics** to use `OptionUtils.parseOptions()` with a schema/descriptor pattern
- **Standard help format**: Markdown block with `**Usage:**`, `**Options:**`, `**Examples:**`

### 2.2 Error Handling & Messages

**Current State:**
- ✅ **JavaMagics**: friendly error messages using `display(..., "text/markdown")` — e.g., "Class `X` not found in file `Y`."
- ✅ **JavaDBMSMagics**: `try-catch` with context, displays error messages inline
- ⚠️ **CompilerMagics, ClasspathMagics**: throws `RuntimeException` on error (not user-friendly)
- ⚠️ **ShellMagics**: logs error but may not surface to user cleanly
- ⚠️ **TimeItMagics**: prints to stdout/stderr; no structured error output

**Problems:**
- **Inconsistent error presentation**: some throw exceptions, some print, some use Display API
- **No structured error format**: users can't parse errors programmatically (e.g., for automated notebooks)
- **Missing validation**: many magics don't validate args before attempting operations

**Recommendation:**
- **Standardize error handling**:
  - Use `display(errorMessage, "text/markdown")` for user-facing errors
  - Log exceptions via `@Slf4j` logger for debugging (avoid noisy stdout)
  - Optionally support `--verbose` flag to show stack traces
- **Add input validation** to all magics (check arg count, file existence, etc.) before execution
- **Structured error output option**: `--format=json` could emit `{"error": "message", "type": "ArgumentError"}` for automation

### 2.3 Output Formatting

**Current State:**
- ✅ **JavaMagics**: unified via `OutputUtils.formatAndDisplay()` — supports `--raw` (plain text) and `--fenced` (Markdown fenced code block)
- ✅ **JavaDBMSMagics**: `%%sqlAsTable` supports `format=HTML/CSV`; `%%rdbmsSchema` supports `SVG`/`PNG`
- ⚠️ **JavaPlantUMLMagics**: SVG/PNG but no JSON/text fallback
- ⚠️ **TimeItMagics**: only `LongSummaryStatistics.toString()` (not machine-readable)
- ⚠️ **ClasspathMagics, MagicsTool**: returns lists or prints to stdout — no format control

**Problems:**
- **Inconsistent MIME types**: some use `text/markdown`, some `text/html`, some `text/plain` without user control
- **No machine-readable output**: hard to extract data from notebooks for automation (e.g., CI/CD pipelines)
- **Missing format options**: many magics don't support `--format=json/csv/html` despite outputting structured data

**Recommendation:**
- **Extend `OutputUtils`** to support:
  - `--format=raw|fenced|json|csv|html|svg|png`
  - Auto-detect MIME type from format
  - JSON output for structured data (e.g., `%%timeIt` → `{"epochs": [...], "total": {...}}`)
- **Apply uniformly** to all magics that output data (compilation results, query results, benchmark stats, etc.)
- **Document MIME types** in help text so users know what to expect

### 2.4 Naming & Aliasing

**Current State:**
- ✅ **Good aliases**: `%%timeIt` → `time`, `timeit`; `%listMagic` → `list`; `%maven` → `addMavenDependency`, `addMavenDependencies`
- ⚠️ **Inconsistent naming**: `%pom` vs `%loadFromPOM`, `%%compile` vs `%%mycompile`, `%%shell` vs `%%myshell` vs `%%commonshell`
- ⚠️ **Missing verb consistency**: some use `list` (line), some use `List` (cell), some omit verb (e.g., `%classpath` vs `%listClasspath`)

**Problems:**
- **Overlapping magics confuse users**: `%%compile` vs `%%mycompile` — which should I use?
- **No naming convention**: verb-noun (e.g., `listMagics`) vs noun-only (e.g., `classpath`) vs prefix (e.g., `javasrc*`)

**Recommendation:**
- **Establish naming convention**:
  - **Discovery/Inspection**: `list*`, `show*`, `get*` (e.g., `%listMagics`, `%showClasspath`)
  - **Modification**: `add*`, `set*`, `compile*` (e.g., `%addJars`, `%%compile`)
  - **Extraction**: `extract*`, `javasrc*` (keep `javasrc*` as domain-specific prefix)
  - **Rendering/Visualization**: `render*`, `draw*` (e.g., `%%renderPlantUML`, `%%drawSchema`)
- **Consolidate duplicates**:
  - Merge `%%compile` and `%%mycompile` → keep `%%compile`, deprecate `%%mycompile` (or make it an alias)
  - Merge shell magics (`%%shell`, `%%myshell`, `%%commonshell`) → keep `%%shell`, add options for shell type (`--shell=bash|zsh`)
  - Merge `%pom` and `%loadFromPOM` → keep `%loadFromPOM`, make `%pom` an alias
- **Add semantic aliases**: e.g., `%jars` → `%addJars`, `%classpath` → `%addClasspath` (keep short names as primary, add verbose aliases)

---

## 3. Duplication & Refactoring Opportunities

### 3.1 Identified Duplications

| Functionality | Implementations | Recommendation |
|---------------|-----------------|----------------|
| **Compilation** | `%%compile` (JavaCompilerMagics), `%%mycompile` (CompilerMagics) | **Consolidate**: Keep `%%compile` (more mature, verbose logging). Deprecate `%%mycompile`. Extract common logic to `CompilerUtils`. |
| **Shell Execution** | `%%shell` (ShellMagics), `%%myshell` (MyShellMagics), `%%commonshell`, `%cmd` (MagicsTool) | **Consolidate**: Keep `%%shell` (cell) and `%cmd` (line). Add `--shell=bash/zsh/sh` option. Remove `%%myshell`, `%%commonshell`. |
| **File I/O** | `%read`, `%load` (both read files), `%write` (line), `%%write` (cell) | **Rationalize**: `%load` for cells (load & edit), `%read` for inline use (assign to variable). Merge cell and line `%%write` → single `%%write` that detects context. |
| **POM Loading** | `%pom`/`%loadFromPOM` (line), `%%pom`/`%%loadFromPOM` (cell) | **Consolidate**: Line magic is sufficient. Deprecate cell variant (or make it call line magic). |
| **Option Parsing** | Manual parsing in JavaDBMSMagics, TimeItMagics, JavaPlantUMLMagics | **Centralize**: Migrate all to `OptionUtils` with schema-based approach. |
| **Path Resolution** | `PathResolver` (JavaMagics), manual resolution in MagicsTool, etc. | **Centralize**: Use `PathResolver` for all file-path resolution (relative to workspace, notebook dir, `--src` base). |
| **Output Formatting** | `OutputUtils` (JavaMagics), manual HTML in JavaDBMSMagics, manual SVG in JavaPlantUMLMagics | **Centralize**: Extend `OutputUtils` to handle HTML, CSV, JSON, SVG. All magics delegate to it. |

### 3.2 Refactoring Roadmap

**Phase 1: Centralize Core Utilities (Week 1)**
- [ ] **Extend `OptionUtils`**: add schema/descriptor support, built-in `--help` handling, flag parsing (`--flag`), key=value, positional
- [ ] **Extend `OutputUtils`**: add `format=json/csv/html/svg/png`, MIME type mapping, error formatting
- [ ] **Extend `PathResolver`**: add workspace-root detection, notebook-relative paths, `--base` option
- [ ] **Create `ValidationUtils`**: common arg validation (e.g., `requireNonEmpty`, `requireFileExists`, `requireInt`)

**Phase 2: Migrate Existing Magics (Week 2-3)**
- [ ] **ClasspathMagics**: add `--help`, validate glob patterns, structured output
- [ ] **CompilerMagics**: consolidate with JavaCompilerMagics → single `%%compile`, add `--help`, JSON output option
- [ ] **ShellMagics**: consolidate 3 variants → single `%%shell`, add `--shell=bash/zsh`, `--timeout=Ns`, `--help`
- [ ] **TimeItMagics**: migrate to `OptionUtils`, add `--format=json` output, `--help`
- [ ] **MagicsTool**: migrate `%load`/`%read`/`%write` to `OptionUtils`, add `--help`, consolidate cell/line variants
- [ ] **JavaPlantUMLMagics**: migrate to `OptionUtils`, add `--help`, `--format=svg/png`
- [ ] **JavaDBMSMagics**: migrate to `OptionUtils`, add `--help` for both `%%rdbmsSchema` and `%%sqlAsTable`
- [ ] **JavaMagics**: add `--help` to remaining `javasrc*` magics (MethodByAnnotationName, InterfaceByName, ClassByName, List)

**Phase 3: Add Missing Features (Week 4)**
- [ ] **Deprecation warnings**: emit deprecation warnings for `%%mycompile`, `%%myshell`, `%%commonshell`, duplicate `%pom` cell magic
- [ ] **Add `%showClasspath`**: list current classpath (for debugging)
- [ ] **Add `%showMavenRepos`**: list current Maven repos
- [ ] **Add `%%javasrcPackage`**: extract entire package source (all classes in package)
- [ ] **Add `%%generateJavadoc`**: generate Javadoc for a class and display inline
- [ ] **Add `--verbose`/`-v` global flag**: show detailed logs/stack traces when present
- [ ] **Add `--dry-run` option**: preview what magic will do without executing (for `%%compile`, `%%shell`, etc.)

**Phase 4: Documentation & Examples (Week 5)**
- [ ] **Update `README.md`**: document all magics with usage examples
- [ ] **Create `docs/magics/`**: individual doc files for each magic family (classpath, compilation, javasrc, dbms, shell, etc.)
- [ ] **Update `ijava_sample_notebook.ipynb`**: add comprehensive examples for all magics with inline help demonstrations
- [ ] **Create `docs/magics/CHEAT_SHEET.md`**: quick-reference guide for common tasks

---

## 4. Feature Coverage Gap Analysis

### 4.1 Missing High-Value Features

| Feature | Current State | Proposed Magic | Priority | Rationale |
|---------|---------------|----------------|----------|-----------|
| **Classpath introspection** | None | `%showClasspath`, `%listJars` | **High** | Users often need to debug classpath issues; currently blind. |
| **Dependency conflict resolution** | Logs only | `%resolveMavenConflicts --tree` | **High** | Maven dep conflicts are common pain point; show dep tree with conflicts highlighted. |
| **Source code navigation** | Javasrc magics (class/method level) | `%%javasrcPackage`, `%%javasrcImports` | **Medium** | Extract all classes in package, list imports for a class (useful for refactoring). |
| **Javadoc generation** | None | `%%generateJavadoc <ClassName>` | **Medium** | Generate and display Javadoc inline (HTML or Markdown). |
| **Code formatting** | None | `%%formatJava` (Google Java Format), `%%formatCode` | **Medium** | Format cell body with standard Java formatter before compilation. |
| **Linting/Static Analysis** | None | `%%checkstyle`, `%%pmd`, `%%spotbugs` | **Low** | Run linters on cell body, display violations inline. |
| **REPL enhancements** | None | `%history`, `%recall <n>`, `%vars` | **Medium** | Show eval history, recall previous cell, list variables in scope. |
| **Notebook metadata** | None | `%notebookInfo`, `%setMetadata key=value` | **Low** | Show/set notebook metadata (kernel version, IJava version, etc.). |
| **Interactive prompts** | None | `%prompt "Enter value:"` | **Low** | Prompt user for input in cell (useful for interactive demos). |
| **Plot integration** | None | `%%plot <data>` (delegate to existing viz libraries) | **Low** | Quick plotting for arrays/lists (delegate to existing Java viz libs like XChart). |
| **Export to script** | None | `%exportNotebook --format=java` | **Low** | Export notebook cells as standalone Java script. |
| **Profiling** | `%%timeIt` (basic) | `%%profile --flamegraph`, `%%memProfile` | **Medium** | Detailed profiling (CPU, memory) with visual reports. |
| **Test execution** | None | `%%test <TestClass>` | **Medium** | Run JUnit tests inline, display results as HTML table. |
| **Docker integration** | None | `%%dockerRun <image> <cmd>` | **Low** | Run command in Docker container, capture output. |

### 4.2 Feature Prioritization

**Tier 1 (Next Release): Core UX Improvements**
1. Consolidate duplicate magics (shell, compile, pom)
2. Add `--help` to all magics
3. Centralize option parsing and output formatting
4. Add `%showClasspath` and `%showMavenRepos` for debugging
5. Improve error messages across all magics

**Tier 2 (Next+1): Enhanced Introspection & Automation**
6. Add `%resolveMavenConflicts --tree`
7. Add `%%javasrcPackage` and `%%javasrcImports`
8. Add `--format=json/csv` to all data-emitting magics
9. Add `%%generateJavadoc`
10. Add `%history` and `%recall`

**Tier 3 (Future): Advanced Features**
11. Add `%%formatJava` (code formatting)
12. Add `%%profile` (detailed profiling)
13. Add `%%test` (JUnit integration)
14. Add linting magics (`%%checkstyle`, `%%pmd`)
15. Add `%exportNotebook --format=java`

---

## 5. Discoverability & Help System

### 5.1 Current Help Mechanisms

| Mechanism | Coverage | Quality | Issues |
|-----------|----------|---------|--------|
| `%list`, `%listLineMagic`, `%listCellMagic` | ✅ All magics listed | ✅ Good | Doesn't show usage or description |
| Per-magic `--help` | ⚠️ Only `%%javasrcMethodByName`, `%%timeIt` | ✅ Good (Markdown formatted) | Inconsistent; most magics lack help |
| Inline error messages | ⚠️ Partial (JavaMagics) | ✅ Good (Markdown) | Not all magics have friendly errors |
| `README.md` documentation | ⚠️ Partial | ⚠️ Outdated | Doesn't cover new javasrc*, dbms magics |
| Example notebooks | ⚠️ `ijava_sample_notebook.ipynb` | ⚠️ Partial | Doesn't cover all magics comprehensively |

### 5.2 Help System Improvements

**Standard Help Format (Template):**
```markdown
**Usage:** `%%magicName [options] <requiredArg> [optionalArg]`

**Purpose:** [One-sentence description]

**Options:**
- `--option1 <value>`: [Description]
- `--flag`: [Description]
- `key=value`: [Description]

**Examples:**
- `%%magicName arg1 arg2` — [What it does]
- `%%magicName --option1=value arg1` — [What it does]

**See also:** [Related magics]
```

**Recommendations:**
- [ ] **Add `--help`/`-h` to ALL magics**: Use `OptionUtils` to detect and short-circuit
- [ ] **Consistent help format**: All help text follows template above
- [ ] **Interactive help**: `%help <magicName>` magic that displays help for any magic
- [ ] **Contextual help**: If user runs magic with invalid args, auto-display help (not just error)
- [ ] **Tooltips in notebook UI**: (Future) VS Code extension to show magic help on hover

---

## 6. Proposed Action Plan

### 6.1 Immediate Actions (Next Sprint)

**Goal:** Stabilize core UX, eliminate duplication, achieve consistency

1. **Consolidate Duplicate Magics** (2 days)
   - [ ] Merge `%%mycompile` into `%%compile` (deprecate `%%mycompile`)
   - [ ] Merge `%%myshell`, `%%commonshell` into `%%shell` (deprecate others)
   - [ ] Merge cell/line `%pom` → keep line magic, deprecate cell
   - [ ] Add deprecation warnings to removed magics

2. **Centralize Utilities** (3 days)
   - [ ] Extend `OptionUtils` with flag support, schema-based parsing, built-in `--help` detection
   - [ ] Extend `OutputUtils` with JSON/CSV/HTML support, MIME type mapping
   - [ ] Create `ValidationUtils` for common arg validation
   - [ ] Document utility APIs in `docs/magics/UTILITIES.md`

3. **Add `--help` to All Magics** (3 days)
   - [ ] Add help text to: ClasspathMagics, CompilerMagics, ShellMagics, MagicsTool, JavaPlantUMLMagics, JavaDBMSMagics, remaining JavaMagics
   - [ ] Use consistent Markdown template
   - [ ] Test help display in notebook

4. **Improve Error Handling** (2 days)
   - [ ] Standardize error display: use `display(error, "text/markdown")`
   - [ ] Add input validation to all magics (use `ValidationUtils`)
   - [ ] Test error scenarios in notebook

5. **Update Documentation** (2 days)
   - [ ] Update `README.md` with complete magic listing and examples
   - [ ] Update `ijava_sample_notebook.ipynb` with help demonstrations
   - [ ] Create `docs/magics/QUICK_START.md` cheat sheet

**Total: ~12 days** (2.5 weeks)

### 6.2 Short-Term Goals (Next Release)

6. **Add High-Value Missing Features** (1 week)
   - [ ] `%showClasspath`
   - [ ] `%showMavenRepos`
   - [ ] `%resolveMavenConflicts --tree`
   - [ ] `%%javasrcPackage`
   - [ ] `%history`, `%recall`

7. **Add Structured Output Options** (1 week)
   - [ ] `--format=json` for: `%%timeIt`, `%%compile`, `%%sqlAsTable`, `%jars`, `%classpath`
   - [ ] Document JSON schema for each output

8. **Testing & Quality Assurance** (1 week)
   - [ ] Create integration tests for all magics (run from notebook)
   - [ ] Smoke-test all help text
   - [ ] Verify all examples in docs work

**Total: ~3 weeks**

### 6.3 Mid-Term Goals (Next+1 Release)

9. **Advanced Introspection** (2 weeks)
   - [ ] `%%generateJavadoc`
   - [ ] `%%javasrcImports`
   - [ ] `%%profile --flamegraph`

10. **Code Quality Integrations** (2 weeks)
    - [ ] `%%formatJava` (Google Java Format)
    - [ ] `%%checkstyle`, `%%pmd` (optional: requires deps)

11. **Automation Enhancements** (1 week)
    - [ ] `%exportNotebook --format=java`
    - [ ] `--dry-run` flag for destructive operations

**Total: ~5 weeks**

### 6.4 Long-Term Vision (Future)

- **Magic Profiles/Presets**: `%useProfile data-science` loads curated set of magics + deps (e.g., Apache Spark, Tablesaw)
- **Interactive Widgets**: `%prompt`, `%slider`, `%dropdown` for notebook interactivity
- **VS Code Extension**: Magic auto-complete, hover help, inline error squiggles
- **Community Magics Repository**: Plugin system for user-contributed magics
- **Cloud Integration**: `%%runOnCloud <provider>` for remote execution

---

## 7. Success Metrics

### 7.1 Quantitative

- **Help Coverage**: 100% of magics have `--help` text
- **Consistency Score**: 100% of magics use `OptionUtils`, `OutputUtils`, `ValidationUtils`
- **Duplication Reduction**: 0 overlapping magics (currently 3 shell variants, 2 compile variants)
- **Error Clarity**: 100% of magics display friendly error messages (not stack traces)
- **Documentation Coverage**: 100% of magics documented in `README.md` + example notebook

### 7.2 Qualitative

- **User Feedback**: Positive sentiment in GitHub issues/discussions (target: >80% positive)
- **Discoverability**: Users can find and use magics without reading external docs (measured via user studies or issues)
- **Consistency**: Users report that magics "feel" consistent (survey or qualitative feedback)

---

## 8. Risks & Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Breaking Changes**: Consolidating magics may break existing notebooks | High | Add deprecation warnings first; provide migration guide; keep old names as aliases for 1 release cycle. |
| **Scope Creep**: Too many new features delay core improvements | Medium | Prioritize Tier 1 (core UX) over Tier 2/3 (advanced features); release incrementally. |
| **Testing Burden**: More magics = more tests | Medium | Create reusable test harness (e.g., `MagicTestRunner` that exercises all magics with valid/invalid inputs). |
| **Community Resistance**: Users may prefer current magic names/behavior | Low | Gather feedback via GitHub issue/discussion before finalizing consolidation plan. |

---

## 9. Conclusion & Recommendations

**Current State:**
- IJava magics provide strong foundation for Java notebook workflows (compilation, source inspection, database visualization, PlantUML rendering)
- Recent improvements (JavaMagics refactor, JavaDBMSMagics enhancements) show clear path toward consistency

**Key Problems:**
1. **Duplication**: 3 shell magics, 2 compile magics, overlapping file I/O
2. **Inconsistency**: Different option parsing, error handling, output formatting across magics
3. **Poor Discoverability**: Most magics lack `--help`; documentation incomplete

**Recommended Path Forward:**
1. **Phase 1 (Immediate)**: Consolidate duplicates, centralize utilities, add `--help` to all magics
2. **Phase 2 (Short-term)**: Add high-value missing features (`%showClasspath`, `%resolveMavenConflicts`, `%%javasrcPackage`)
3. **Phase 3 (Mid-term)**: Add advanced features (profiling, Javadoc generation, code formatting)
4. **Phase 4 (Long-term)**: Explore magic profiles, VS Code extension, community plugins

**Expected Outcome:**
- **Best-in-class Java Jupyter UX**: Consistent, discoverable, powerful magics that feel like a cohesive toolkit
- **Reduced maintenance burden**: Centralized utilities = less duplication, easier to add new features
- **Happy users**: Clear documentation, friendly error messages, predictable behavior

**Next Step:**
- Review this plan with maintainers/contributors
- Create GitHub issues for each Phase 1 task
- Begin consolidation work (start with shell magics, then compile magics)

---

**Document Version:** 1.0  
**Last Updated:** January 15, 2026  
**Author:** GitHub Copilot (audit commissioned by user `bruno`)
