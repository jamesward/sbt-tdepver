# AGENTS.md

## Project

`sbt-tdepver` is an sbt 2 AutoPlugin. It implements deferred dependency versions such as:

```scala
"dev.zio" %% "zio-http-testkit" % ("dev.zio" %% "zio-http").version
```

## Design constraints

- Keep the public syntax in `TdepverPlugin.autoImport`.
- A `VersionRef` is symbolic; it must never resolve dependencies while `build.sbt` settings are loading.
- Deferred `ModuleID`s use a private marker revision and namespaced extra attributes. They must be removed from the probe graph and fully concretized before sbt's normal update.
- Preserve sbt's standard final `update` task so dependency trees, classpaths, locking, eviction checks, and publication see concrete `ModuleID`s.
- The probe graph must contain only ordinary dependencies. Referenced modules must be selected, non-evicted modules in that graph.

## Validation
- `dependencyCleanup` uses `updateFull` caller edges. Redundancy removes only the root-to-module edge and tests alternate reachability; tdepver sources must remain transitively reachable with the target blocked.
- Keep report ordering and rendered declarations deterministic because scripted tests and users rely on copy-pastable output.

Run all plugin validation with:

```text
./sbt 'compile; test; scripted'
```

Scripted tests live under `src/sbt-test/<group>/<test>/`. They receive the local plugin version through `-Dplugin.version`.
