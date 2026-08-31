package com.jamesward.sbttdepver

import sbt.librarymanagement.*

private object DependencyRendering:
  def isRenderable(module: ModuleID): Boolean =
    val supportedCrossVersion = module.crossVersion match
      case _: Disabled => true
      case binary: Binary => binary.prefix.isEmpty && binary.suffix.isEmpty
      case _ => false

    supportedCrossVersion &&
      module.explicitArtifacts.isEmpty &&
      module.inclusions.isEmpty &&
      module.exclusions.isEmpty &&
      module.extraAttributes.isEmpty &&
      module.branchName.isEmpty

  def renderDeclaration(module: ModuleID): String =
    s"${renderCoordinate(module.organization, module.name, isCrossVersioned(module))} % " +
      s"${quote(module.revision)}${renderConfiguration(module.configurations)}"

  def renderTdepver(
      target: ModuleID,
      source: ModuleID,
      scalaModuleInfo: Option[ScalaModuleInfo]
  ): String =
    val targetCoordinate = renderCoordinate(
      target.organization,
      target.name,
      isCrossVersioned(target)
    )
    val sourceCoordinate = sourceCoordinateParts(source, scalaModuleInfo)
    s"$targetCoordinate % (${renderCoordinate(sourceCoordinate._1, sourceCoordinate._2, sourceCoordinate._3)}).version" +
      renderConfiguration(target.configurations)

  def baseName(module: ModuleID): String =
    module.name.reverse.dropWhile(_ != '_').drop(1).reverse match
      case "" => module.name
      case value => value

  def commonPrefixLength(left: String, right: String): Int =
    left.split('-').zip(right.split('-')).takeWhile(_ == _).length

  private def sourceCoordinateParts(
      module: ModuleID,
      scalaModuleInfo: Option[ScalaModuleInfo]
  ): (String, String, Boolean) =
    val suffixes = scalaModuleInfo.toVector.flatMap: info =>
      val platformSuffix = info.platform
        .filterNot(_ == Platform.jvm)
        .map(value => s"_$value")
        .getOrElse("")
      Vector(s"${platformSuffix}_${info.scalaBinaryVersion}", s"_${info.scalaFullVersion}")
    val suffix = suffixes.sortBy(-_.length).find(module.name.endsWith)
    suffix match
      case Some(value) => (module.organization, module.name.stripSuffix(value), true)
      case None => (module.organization, module.name, false)

  private def renderCoordinate(
      organization: String,
      name: String,
      crossVersioned: Boolean
  ): String =
    val operator = if crossVersioned then "%%" else "%"
    s"${quote(organization)} $operator ${quote(name)}"

  private def renderConfiguration(configurations: Option[String]): String =
    configurations match
      case None => ""
      case Some(value) =>
        val rendered = value match
          case "compile" => "Compile"
          case "test" => "Test"
          case "runtime" => "Runtime"
          case "provided" => "Provided"
          case other => quote(other)
        s" % $rendered"

  private def isCrossVersioned(module: ModuleID): Boolean =
    !module.crossVersion.isInstanceOf[Disabled]

  private def quote(value: String): String =
    s"\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
