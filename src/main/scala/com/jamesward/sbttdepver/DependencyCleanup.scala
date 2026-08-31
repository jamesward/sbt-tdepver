package com.jamesward.sbttdepver

import sbt.librarymanagement.*

private object DependencyCleanup:
  import DependencyGraph.*
  import DependencyRendering.*

  def analyze(
      dependencies: Seq[ModuleID],
      report: UpdateReport,
      project: ModuleID,
      scalaModuleInfo: Option[ScalaModuleInfo]
  ): DependencyCleanupReport =
    val explicit = dependencies
      .filterNot(DeferredModule.decode(_).isDefined)
      .filterNot(isAutomaticScalaDependency(_, scalaModuleInfo))
      .filter(isRenderable)
      .toVector

    val root = key(concrete(project, scalaModuleInfo))
    val graphs = report.configurations.map(configuration =>
      configuration.configuration.name -> graph(configuration)
    ).toMap

    val concreteDependencies = explicit.map(dependency =>
      dependency -> concrete(dependency, scalaModuleInfo)
    )
    val explicitKeys = concreteDependencies.map((_, dependency) => key(dependency)).toSet

    val redundant = concreteDependencies.filter: (declared, resolved) =>
      val configurations = configurationNames(declared)
      configurations.nonEmpty && configurations.forall: configuration =>
        graphs.get(configuration).exists(_.reachable(
          root,
          key(resolved),
          ignoredEdges = Set(root -> key(resolved))
        ))

    val redundantKeys = redundant.map((_, dependency) => key(dependency)).toSet
    val redundantReport = redundant
      .map: (declared, _) =>
        RedundantDependency(
          declared.organization,
          declared.name,
          renderDeclaration(declared)
        )
      .sortBy(value => (value.organization, value.name, value.declaration))

    val neededWithCandidates = concreteDependencies
      .filterNot((_, dependency) => redundantKeys.contains(key(dependency)))
      .map: (declared, resolved) =>
        (declared, resolved, familyCandidates(declared, resolved, graphs))
      .filter(_._3.nonEmpty)

    val potentialTargetKeys = neededWithCandidates.map((_, resolved, _) => key(resolved)).toSet
    val removedDirectEdges = (redundantKeys ++ potentialTargetKeys).map(root -> _)

    val suggestions = neededWithCandidates
      .flatMap: (declared, resolved, candidates) =>
        suggestion(
          declared,
          resolved,
          candidates,
          root,
          graphs,
          explicitKeys,
          redundantKeys,
          removedDirectEdges,
          scalaModuleInfo
        )
      .sortBy(value => (value.organization, value.name, value.replacement))

    DependencyCleanupReport(redundantReport, suggestions)

  private def familyCandidates(
      declared: ModuleID,
      resolved: ModuleID,
      graphs: Map[String, Graph]
  ): Vector[ModuleID] =
    val configurations = configurationNames(declared)
    val target = key(resolved)
    val revisions = configurations
      .flatMap(configuration => graphs.get(configuration).flatMap(_.module(target)))
      .map(_.revision)
      .distinct

    revisions match
      case Vector(revision) =>
        configurations
          .map(configuration =>
            graphs.get(configuration).toVector.flatMap(_.modules.values).filter(candidate =>
              candidate.organization == resolved.organization &&
                candidate.revision == revision &&
                key(candidate) != target &&
                commonPrefixLength(baseName(resolved), baseName(candidate)) > 0
            )
          )
          .map(_.map(key).toSet)
          .reduceOption(_ intersect _)
          .getOrElse(Set.empty)
          .toVector
          .flatMap(candidate =>
            configurations.iterator.flatMap(graphs.get).flatMap(_.module(candidate)).nextOption()
          )
      case _ => Vector.empty

  private def suggestion(
      declared: ModuleID,
      resolved: ModuleID,
      candidates: Vector[ModuleID],
      root: ModuleKey,
      graphs: Map[String, Graph],
      explicitKeys: Set[ModuleKey],
      redundantKeys: Set[ModuleKey],
      removedDirectEdges: Set[(ModuleKey, ModuleKey)],
      scalaModuleInfo: Option[ScalaModuleInfo]
  ): Option[TdepverSuggestion] =
    val configurations = configurationNames(declared)
    val eligible = candidates
      .filter: candidate =>
        val candidateKey = key(candidate)
        (!explicitKeys.contains(candidateKey) || redundantKeys.contains(candidateKey)) &&
          configurations.forall(configuration =>
            graphs.get(configuration).exists(_.reachable(
              root,
              candidateKey,
              ignoredEdges = removedDirectEdges
            ))
          )
      .sortBy(candidate =>
        (
          -commonPrefixLength(baseName(resolved), baseName(candidate)),
          baseName(candidate).length,
          candidate.name
        )
      )

    eligible.headOption.map: source =>
      TdepverSuggestion(
        declared.organization,
        declared.name,
        source.organization,
        baseName(source),
        renderDeclaration(declared),
        renderTdepver(declared, source, scalaModuleInfo)
      )

  private def concrete(
      module: ModuleID,
      scalaModuleInfo: Option[ScalaModuleInfo]
  ): ModuleID =
    scalaModuleInfo match
      case Some(info) =>
        CrossVersion(info.scalaFullVersion, info.scalaBinaryVersion)(
          module.withPlatformOpt(info.platform)
        )
      case None => module

  private def configurationNames(module: ModuleID): Vector[String] =
    module.configurations match
      case None => Vector(Configurations.Compile.name)
      case Some(value) =>
        value
          .split("[;,]")
          .iterator
          .map(_.trim)
          .filter(_.nonEmpty)
          .map(_.split("->", 2).head.trim)
          .toVector
          .distinct

  private def isAutomaticScalaDependency(
      module: ModuleID,
      scalaModuleInfo: Option[ScalaModuleInfo]
  ): Boolean =
    scalaModuleInfo.exists(info =>
      module.organization == info.scalaOrganization &&
        (module.name == "scala-library" || module.name == "scala3-library")
    )
