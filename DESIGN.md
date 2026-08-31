# Design

The build DSL creates a symbolic relationship, not a version string:

```scala
"org" %% "target" % ("org" %% "source").version
```

`OrganizationArtifactName` exposes only `% (String)`. Scala 3 selects the plugin's extension `% (VersionRef)` when the member overload is inapplicable. Both extensions call the public `% (String)` builder with a private marker, avoiding reflection into sbt's private coordinate fields.

The marker `ModuleID` stores the source coordinate and cross-version strategy in namespaced extra attributes. The plugin decorates `moduleSettings`:

1. Remove all marked dependencies.
2. Resolve the remaining descriptor with sbt's configured `DependencyResolution`.
3. Find each source's selected, non-evicted revision after applying its cross-version strategy.
4. Replace each marker with a normal `ModuleID` using that revision and remove marker attributes.
5. Return the concrete module settings to sbt's unchanged `update` task.

The final update therefore remains authoritative and retains sbt's normal caching, eviction checking, classpath generation, dependency trees, locking, and publishing behavior.
