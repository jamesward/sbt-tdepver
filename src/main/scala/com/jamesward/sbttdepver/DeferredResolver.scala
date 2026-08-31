package com.jamesward.sbttdepver

import sbt.internal.util.MessageOnlyException
import sbt.librarymanagement.*
import sbt.util.Logger

private object DeferredResolver:
  def resolve(
      settings: ModuleSettings,
      dependencyResolution: DependencyResolution,
      updateConfiguration: UpdateConfiguration,
      warningConfiguration: UnresolvedWarningConfiguration,
      log: Logger
  ): ModuleSettings =
    settings match
      case descriptor: ModuleDescriptorConfiguration =>
        val deferred = descriptor.dependencies.flatMap(DeferredModule.decode)
        if deferred.isEmpty then descriptor
        else
          val probeSettings = descriptor.withDependencies(
            descriptor.dependencies.filterNot(_.revision == DeferredModule.MarkerRevision)
          )
          val probe = dependencyResolution.moduleDescriptor(probeSettings)
          val report = dependencyResolution
            .update(probe, updateConfiguration, warningConfiguration, log)
            .fold(
              warning => throw warning.resolveException,
              identity
            )

          val resolvedDependencies = descriptor.dependencies.map: dependency =>
            DeferredModule.decode(dependency) match
              case None => dependency
              case Some(value) =>
                val revision = selectedRevision(value.source, value.target, descriptor, report)
                log.debug(
                  s"sbt-tdepver selected ${value.source.organization}:${value.source.name}:$revision " +
                    s"for ${value.target.organization}:${value.target.name}"
                )
                DeferredModule.concretize(value.target, revision)

          descriptor.withDependencies(resolvedDependencies)
      case unsupported =>
        throw new MessageOnlyException(
          s"sbt-tdepver requires ModuleDescriptorConfiguration, found ${unsupported.getClass.getName}"
        )

  private def selectedRevision(
      source: ModuleID,
      target: ModuleID,
      descriptor: ModuleDescriptorConfiguration,
      report: UpdateReport
  ): String =
    val concreteSource = descriptor.scalaModuleInfo match
      case Some(scalaInfo) =>
        val withPlatform = source.withPlatformOpt(scalaInfo.platform)
        CrossVersion(scalaInfo.scalaFullVersion, scalaInfo.scalaBinaryVersion)(withPlatform)
      case None if source.crossVersion.isInstanceOf[Disabled] => source
      case None =>
        throw new MessageOnlyException(
          s"sbt-tdepver cannot cross-version ${source.organization}:${source.name} without Scala module information"
        )

    val configurations = targetConfigurations(target, descriptor)
    val revisions = report.configurations
      .filter(configuration => configurations.contains(configuration.configuration.name))
      .flatMap(_.modules)
      .filter(module =>
        !module.evicted &&
          module.module.organization == concreteSource.organization &&
          module.module.name == concreteSource.name
      )
      .map(_.module.revision)
      .distinct

    revisions match
      case Vector(revision) => revision
      case Vector() =>
        throw new MessageOnlyException(
          s"sbt-tdepver could not find selected transitive dependency " +
            s"${concreteSource.organization}:${concreteSource.name} " +
            s"in ${configurations.toVector.sorted.mkString(", ")}"
        )
      case many =>
        throw new MessageOnlyException(
          s"sbt-tdepver found multiple selected versions for " +
            s"${concreteSource.organization}:${concreteSource.name} " +
            s"in ${configurations.toVector.sorted.mkString(", ")}: ${many.mkString(", ")}"
        )

  private def targetConfigurations(
      target: ModuleID,
      descriptor: ModuleDescriptorConfiguration
  ): Set[String] =
    target.configurations match
      case None => Set(descriptor.defaultConfiguration.getOrElse(Configurations.Compile).name)
      case Some(value) =>
        value
          .split("[;,]")
          .iterator
          .map(_.trim)
          .filter(_.nonEmpty)
          .map(_.split("->", 2).head.trim)
          .toSet
