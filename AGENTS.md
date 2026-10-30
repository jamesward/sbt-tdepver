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
./sbta 'compile; test; scripted'
```

Scripted tests live under `src/sbt-test/<group>/<test>/`. They receive the local plugin version through `-Dplugin.version`.

## Agent tooling

- For Scala 3 and ZIO code (writing or reviewing it), follow `zen-of-scala`. For design, domain modeling, effects and testing strategy, follow `zen-of-james`. Read the relevant Skills before working.
- Follow the `zen-of-projects` Skill (extract with `./sbta extractSkillsJars` into the gitignored `.kiro/skills/`); this file records only project-specific facts and exceptions.
- MCP server `sbta` (this build's `./sbta --mcp`, configured in `.mcp.json` and `.kiro/settings/mcp.json`, approved in `.claude/settings.json`): use it for all sbt work: `compile`, `test` (`full` for all), `run`, `check` after editing Scala 3 sources, `glob-search` / `inspect` / `symbol-location` for symbols, `sbt-task` for any other command. Its tools are deferred in Claude Code: load them with ToolSearch (search `sbta`). If it's unavailable, say so and use `./sbta <command>`. Logs: `project/target/sbta/mcp.log`.
- Maintenance routine: `.factory/MAINTENANCE.md` (weekly), following the `zen-of-projects` Skill.
