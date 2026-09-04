# IJava 1.4.6-pr12

## Highlights
- Default per-statement timeout is now 60 seconds; override with the `timeout` magic parameter or `IJAVA_TIMEOUT=-1` to disable it.
- Vendored `jupyter-jvm-basekernel` into the repository as a Gradle module for a reproducible, self-contained build.
- Upgraded the build to JDK 25 and Gradle 9.7.1.
- Hardened the Shadow JAR packaging pipeline for duplicate classes, service files, and kernel metadata.
- Fixed kernel installation so `--replace` is honored and installed kernel paths are handled robustly.
- Improved the release smoke test to install through the real `install.py` path.
- Added deterministic magic test coverage and stabilized Maven dependency resolution tests.
- `%%rdbmsSchema` now supports `sourceOnly`, `source-only`, and `--source-only` to display PlantUML source without rendering.
- `%%commonshell` now selects an available shell instead of assuming `/bin/zsh`.
- Added SonarQube multi-branch analysis and optimized CI to use the preinstalled JDK 25 on the `ebpro` ARC runner.

## Upgrade instructions
1. Download `IJava-v1.4.6-pr12.zip`.
2. Unzip it.
3. Install the kernel with the same Python environment used by Jupyter:
   - `python install.py --user --replace`
   - or `python install.py --sys-prefix --replace`
4. Restart Jupyter.

## Requirements
- Java JDK 25.
- A Jupyter-compatible environment with `jupyter_client` available for installation.
