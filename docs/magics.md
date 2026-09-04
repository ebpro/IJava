# Magics

Magics in IJava are very similar to those from IPython. There are:

*   **Line magics**: which are inline function calls via a magic function.

    ```text
    %mavenRepo oss-sonatype-snapshots https://oss.sonatype.org/content/repositories/snapshots/
    %maven io.github.spencerpark:jupyter-jvm-basekernel:2.0.0-SNAPSHOT
    List<String> addedJars = %jars C:/all/my/*.jar
    ```

*   **Cell magics**: which are entire cell function calls that use the body of the cell as a special argument.

    ```xml
    %%loadFromPOM
    <repository>
      <id>oss-sonatype-snapshots</id>
      <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
    </repository>

    <dependency>
      <groupId>io.github.spencerpark</groupId>
      <artifactId>jupyter-jvm-basekernel</artifactId>
      <version>2.0.0-SNAPSHOT</version>
    </dependency>
    ```

The magics simply desugar to calls to `lineMagic` and `cellMagic` in case programmatic access is desired. These functions are in the notebook namespace and have the signatures below. Note the return type which allows for an implicit cast to what ever type is required but there is no safety in these checks.

*   `<T> T lineMagic(String name, java.util.List<String> args)`
*   `<T> T cellMagic(String name, java.util.List<String> args, String body)`

## Magics provided by IJava

Things that are likely to become magics are kernel meta functions or functions that operate on source code. Magics should only be used for things that only appear in a Jupyter-like context and only use string arguments. Other things (like `display` and `render`) should be provided as plain functions.



### jars

Add jars to the notebook classpath.

###### Line magic

*   **arguments**:
    *   _varargs_ list of simple glob paths to jars on the local file system. If a glob matches a directory all files in that directory will be added.



### classpath

Add entries to the notebook classpath.

###### Line magic

*   **arguments**:
    *   _varargs_ list of simple glob paths to entries on the local file system. This includes directories or jars.



### addMavenDependencies

Add maven artifacts to the notebook classpath. All transitive dependencies are also added to the classpath. See also [addMavenRepo](#addmavenrepo).

###### Line magic

*   **aliases**: `addMavenDependency`, `maven`
*   **arguments**:
    *   _varargs_ list of dependency coordinates in the form `groupId:artifactId:[packagingType:[classifier]]:version`



### addMavenRepo

Add a maven repository to search for when using [addMavenDependencies](#addmavendependencies).

###### Line magic

*   **aliases**: `mavenRepo`
*   **arguments**:
    *   repository id
    *   repository url


### loadFromPOM

Load any dependencies specified in a POM. This **ignores** repositories added with [addMavenRepo](#addmavenrepo) as the POM would likely specify it's own.

The cell magic is designed to make it very simple to copy and paste from any READMEs specifying maven POM fragments to use in depending on an artifact (including repositories other than central).

###### Line magic

*   **arguments**:
    *   path to local POM file
    *   _varargs_ list of scope types to filter the dependencies by. Defaults to `compile`, `runtime`, `system`, and `import` if not supplied.

###### Cell magic

*   **arguments**:
    *   _varargs_ list of scope types to filter the dependencies by. Defaults to `compile`, `runtime`, `system`, and `import` if not supplied.
*   **body**:
    A _partial_ POM literal.

    If the body is an xml `<project>` tag, then the body is used as a POM without being modified.

    Otherwise, the magic attempts to build a POM based on the xml fragments it gets.

    `<modelVersion>`, `<groupId>`, `<artifactId>`, and `<version>` are given default values if not supplied which there is no reason to supply other than if they happen to be what is copy-and-pasted.

    All children of `<dependencies>` and `<repositories>` are collected **along with any loose `<dependency>` and `repository` tags**.

    Ex: To add a dependency not in central simply add a valid `<repository>` and `<dependency>` and the magic will take care of putting it together into a POM.

    ```xml
    %%loadFromPOM
    <repository>
      <id>oss-sonatype-snapshots</id>
      <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
    </repository>

    <dependency>
      <groupId>io.github.spencerpark</groupId>
      <artifactId>jupyter-jvm-basekernel</artifactId>
      <version>2.0.0-SNAPSHOT</version>
    </dependency>
    ```

    The line form `%loadFromPOM <pom.xml> [scopes...]` loads a local POM file. `pom` is an alias for both forms.

## Fork extensions (ebpro/IJava)

The magics below are provided by this fork on top of the original set. Every magic also supports `--help` / `-h`.

### JDBC-backed magics

`%%rdbmsSchema`, `%%sqlAsTable` and `%%tableSchema` operate against a JDBC database. Configure the connection with the system properties `jdbc.url` (required) and optionally `jdbc.user` / `jdbc.password` (for example in the `env` section of `kernel.json` or a startup script). Common drivers (H2, HSQLDB, Derby, SQLite, MySQL, PostgreSQL) are attempted automatically before obtaining a `Connection`.

### %%rdbmsSchema

Render the schema of a relational database as a PlantUML ER diagram (SVG or PNG).

###### Cell magic

*   **arguments**: `[<schema>] [SVG|PNG] [--show-source] [handwritten] [include=<regex>] [exclude=<regex>] [scale=<n>]`
*   **body**: optional list of table names (one per line) to restrict the diagram

### %%sqlAsTable

Execute SQL and render the first `SELECT` result as an HTML table (or CSV).

###### Cell magic

*   **arguments**: `[format=HTML|CSV] [max=<n>] [showQuery]` (max defaults to 1000 rows)
*   **body**: one or more SQL statements; the first `SELECT` is rendered

### %%tableSchema / %tableSchema

Show the detailed layout of one or more tables: columns with PK/FK/UNIQUE markers, types, nullability, autoincrement, optional DDL and sample rows.

###### Line magic

*   **arguments**: `[<table>] [--ddl] [--compact] [--sample=<n>]`

###### Cell magic

*   **arguments**: same as the line form
*   **body**: table names, one per line

### %%compile

Compile the cell body with the platform `javac`, add the result to the notebook classpath, and print diagnostics. Supports annotation processors (e.g. Lombok).

###### Cell magic

*   **arguments**: `[--verbose|-v] [--debug|-d] [--nowarn|-w] [--dry-run|-n] [--release=<n>] [--enable-preview] [--output=<dir>] [--classpath=<cp>] [--processor=<fqcn>]* [--processor-path=<cp>] [--processor-option=<kv>]* [--class=<fqcn>] | <fully.qualified.ClassName>`
*   **body**: Java source. A `package` declaration is added automatically when missing.

    ```java
    %%compile --output=demo com.example.Calculator
    public class Calculator {
        public int add(int a, int b) { return a + b; }
    }
    ```

### %%mycompile

Simpler compile variant: `%%mycompile <FullyQualifiedClassName>` with the source in the body. The package is inferred from the class name.

### %%benchmark

Compare the performance of several implementations in one cell and render an SVG bar/line chart. Implementations are separated by a line containing only `---`.

###### Cell magic

*   **arguments**: `[iterations=<n>] [warmup=<n>] [--sweep var=<name> start=<n> end=<n> step=<n>] [--chart]`
*   **body**: the implementations

    ```java
    %%benchmark iterations=10
    int sum = 0; for (int i = 0; i < 1000; i++) sum += i;
    ---
    int sum = IntStream.range(0, 1000).sum();
    ```

> **Note:** timings are measured through jshell snippet evaluation, so they include snippet compilation and dispatch overhead. Use `%%benchmark` for classroom-level comparisons only. For publication-grade microbenchmarks use [JMH](https://github.com/openjdk/jmh), e.g. `%%maven org.openjdk.jmh:jmh-core:1.37`.

### %%time / %%timeit

Run the cell body several times and report min / median / avg / max in nanoseconds.

###### Cell magic

*   **arguments**: `[warmup=<n>] [iterations=<n>]` (defaults: `warmup=1`, `iterations=5`)
*   **aliases**: `time`, `timeit`

### %%classDiagram / %classDiagram

Generate a UML class diagram (PlantUML, rendered to SVG/PNG) for a class or a whole package using classpath scanning.

###### Line / cell magic

*   **target**: `<fully.qualified.ClassName>` or `--package=<pkg>`
*   **options**: `[--svg] [--png] [--uml] [--non-public] [--ancestors] [--depth=<n>] [--interfaces-only] [--classes-only] [--exclude-inherited] [--max=<n>] [--include=<regex>] [--exclude=<regex>] [--out=<file>]`
    *   `--uml` prints the PlantUML source instead of rendering
    *   `--ancestors` follows superclasses/interfaces up to `--depth`

### %%plantUML / %%plantUMLFile

Render PlantUML. `%%plantUML` takes the diagram source in the body; `%%plantUMLFile` takes a path to a `.puml` file. Both accept `[SVG|PNG]` and `--show-source`.

### %%shell / %cmd

Execute shell commands.

###### %%shell (cell)

*   **arguments**: `[--shell=<SHELL>] [--timeout=<SECONDS>]` (default timeout 180s)
*   **body**: the command(s) to run

###### %cmd (line)

*   **arguments**: `<command...>` — runs a single command and prints its output

### %%commonshell / %commonshellcmd

Run commands in a *persistent* shell session (state such as `cd` or `export` survives between cells). `%%commonshell` takes the command in the body; `%commonshellcmd <command...>` is the line form.

### %%write / %write / %read / %load

File helpers.

*   `%%write [path]` — write the cell body to `path` (a temp file is used when omitted)
*   `%write <variable> [path]` — write a variable's value to `path` (or a temp file)
*   `%read <path>` — read a file and return its content as a `String`
*   `%load <path>` — load a `.java` / `.jshell` / `.jsh` / `.ijava` file into the notebook; plain file names are also searched under `docs/notebooks/` and the workspace

### %list / %lineMagic / %cellMagic

List the registered line magics, cell magics, or both.

### %printWithName

Toggle the "print with variable name or source" result decoration (defaults on).

### %printerPrefix / print()

`print(Object)` is a notebook function that prints the value prefixed with the name of the argument expression. `%printerPrefix <prefix>` sets a custom prefix (e.g. `%printerPrefix "db> "`); with no argument it shows the current prefix.

### %reload-class

Reload an already compiled class (by fully qualified name) after recompiling it with `%%compile`.

### %class-info / %javadoc-html / %where

*   `%class-info <name>` — show class metadata (fields, methods, annotations)
*   `%javadoc-html <name>` — render Javadoc for a class as HTML
*   `%where <name>` (alias `%which`) — locate a class: containing jar/classpath entry and, when available, its source file

### %classpath-snapshot

Print the current notebook classpath (useful for reproducing a session).

### %%javasrc* (source extraction)

Extract Java source from files on disk (resolved via `--src=<dir>`) using JavaParser. All variants accept `[--src=<dir>] [--raw] [--fenced]`:

*   `%%javasrcClassByName <ClassName>` — full class source
*   `%%javasrcInterfaceByName <InterfaceName>` — full interface source
*   `%%javasrcConstructorByName <ClassName> <paramTypes...>` — one constructor
*   `%%javasrcMethodByName <ClassName> <methodName|index>` — one method (`methodRegex=<regex>` and `selectIndex=<n>` supported)
*   `%%javasrcMethodByAnnotationName <ClassName> <annotationName>` — methods carrying an annotation
*   `%%javasrcFieldByName <ClassName> <fieldName>` — one field
*   `%%javasrcJavadoc <ClassName> [memberName]` — Javadoc of a class or member
*   `%%javasrcList <ClassName|dir>` — list classes/members found

### %%loadFromPOM alias

`pom` works as an alias for both the line and cell forms of [loadFromPOM](#loadfrompom).