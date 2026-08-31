package com.jamesward.sbttdepver

final case class RedundantDependency(
    organization: String,
    name: String,
    declaration: String
)

final case class TdepverSuggestion(
    organization: String,
    name: String,
    sourceOrganization: String,
    sourceName: String,
    currentDeclaration: String,
    replacement: String
)

final case class DependencyCleanupReport(
    redundantDependencies: Vector[RedundantDependency],
    tdepverSuggestions: Vector[TdepverSuggestion]
):
  def lines: Vector[String] =
    if redundantDependencies.isEmpty && tdepverSuggestions.isEmpty then
      Vector("dependencyCleanup: no cleanup opportunities found")
    else
      Vector("dependencyCleanup:") ++ redundantLines ++ suggestionLines

  private def redundantLines: Vector[String] =
    if redundantDependencies.isEmpty then Vector.empty
    else
      Vector("", "Explicit dependencies already available transitively; remove:") ++
        redundantDependencies.flatMap(value => Vector(s"  ${value.declaration}"))

  private def suggestionLines: Vector[String] =
    if tdepverSuggestions.isEmpty then Vector.empty
    else
      Vector("", "Explicit dependencies that can use a transitive version:") ++
        tdepverSuggestions.flatMap(value =>
          Vector(
            "  replace:",
            s"    ${value.currentDeclaration}",
            "  with:",
            s"    ${value.replacement}"
          )
        )
