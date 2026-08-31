# sbt-tdepver

[![javadocs.dev](https://www.javadocs.dev/com.jamesward/sbt-tdepver_sbt2_3/badge.svg)](https://www.javadocs.dev/com.jamesward/sbt-tdepver_sbt2_3/latest)

An sbt 2 plugin for declaring a dependency whose version should match the version selected for another dependency already present in the transitive graph.

## Setup

Add the plugin to `project/plugins.sbt`:

```scala
addSbtPlugin("com.jamesward" % "sbt-tdepver" % "<version>")
```

The plugin is enabled automatically.

## Usage

```scala
libraryDependencies ++= Seq(
  "com.jamesward" %% "zio-http-guard" % "0.0.1",
  "dev.zio" %% "zio-http-testkit" %
    ("dev.zio" %% "zio-http").version % Test,
)
```

The `zio-http-testkit` revision is deferred. During dependency resolution, sbt-tdepver:

1. Resolves the graph without deferred dependencies.
2. Finds the selected, non-evicted `zio-http` revision.
3. Replaces the deferred `zio-http-testkit` revision with that exact revision.
4. Lets sbt perform its normal cached resolution with the concrete dependency.

Both `%` and `%%` coordinates are supported. Configuration suffixes such as `% Test`, exclusions, classifiers, and other normal `ModuleID` transformations can be applied to the deferred dependency.

## Finding cleanup opportunities

Run:

```text
> dependencyCleanup
```

The task analyzes sbt's resolved caller graph and reports two categories:

- **Explicit dependencies already available transitively.** These declarations can usually be removed. Matching uses organization and resolved artifact name but deliberately ignores the declared version.
- **Needed explicit dependencies that can use a transitive version.** The report prints the current declaration and a copy-pastable replacement using `.version`.

Example:

```text
dependencyCleanup:

Explicit dependencies already available transitively; remove:
  "dev.zio" %% "zio-http" % "3.11.1"

Explicit dependencies that can use a transitive version:
  replace:
    "dev.zio" %% "zio-http-testkit" % "3.11.4" % Test
  with:
    "dev.zio" %% "zio-http-testkit" % ("dev.zio" %% "zio-http").version % Test
```

A `.version` suggestion is emitted only when the target remains necessary and an independently transitive module in the same organization has the selected target revision. Candidates with a shared artifact-name prefix are preferred deterministically. Reachability is checked after removing every redundant dependency and proposed version target together, so the printed replacements are safe to apply as a set. Dependencies already using `.version`, sbt's automatic Scala library, and declarations whose custom attributes cannot be rendered safely are omitted.

The task returns a `DependencyCleanupReport` containing `redundantDependencies` and `tdepverSuggestions`, in addition to printing the report.

## Failure behavior

Resolution fails with a clear error when:

- the referenced dependency is absent from the non-deferred graph;
- the referenced dependency resolves to multiple selected revisions;
- a deferred dependency refers to another deferred dependency; or
- a cross-versioned reference has no Scala module information.

## Limitations

- Requires sbt 2.x and Coursier.
- Deferred references must already be reachable from at least one ordinary dependency. Chained deferred references are not supported.
- Resolution performs a probe before sbt's normal update. Artifact downloads are shared through Coursier's cache, but dependency resolution occurs twice when deferred dependencies are present.
- A published POM contains the concrete version selected at publication time, not the deferred relationship.
