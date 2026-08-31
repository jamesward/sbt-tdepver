package com.jamesward.sbttdepver

import sbt.*
import sbt.Keys.*
import sbt.librarymanagement.ModuleID
import sbt.librarymanagement.DependencyBuilders.OrganizationArtifactName

object TdepverPlugin extends AutoPlugin:
  override def trigger = allRequirements

  object autoImport:
    final class VersionRef private[sbttdepver] (private[sbttdepver] val source: ModuleID)

    @transient
    val dependencyCleanup = taskKey[DependencyCleanupReport](
      "Reports explicit dependencies that are already transitive or can use a transitive version"
    )

    extension (source: OrganizationArtifactName)
      def version: VersionRef = VersionRef(source % DeferredModule.MarkerRevision)

    extension (target: OrganizationArtifactName)
      infix def %(versionRef: VersionRef): ModuleID =
        DeferredModule.encode(target % DeferredModule.MarkerRevision, versionRef.source)

  import autoImport.*

  override lazy val projectSettings: Seq[Def.Setting[?]] = Seq(
    moduleSettings := Def.uncached {
      DeferredResolver.resolve(
        moduleSettings.value,
        dependencyResolution.value,
        updateConfiguration.value,
        (update / unresolvedWarningConfiguration).value,
        streams.value.log
      )
    },
    dependencyCleanup := Def.uncached {
      val report = DependencyCleanup.analyze(
        libraryDependencies.value,
        updateFull.value,
        projectID.value,
        scalaModuleInfo.value
      )
      report.lines.foreach(line => streams.value.log.info(line))
      report
    }
  )
