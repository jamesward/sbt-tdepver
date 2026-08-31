scalaVersion := "3.8.4"

libraryDependencies += "com.lihaoyi" %% "os-lib" % "0.11.5"

val verifyClean = taskKey[Unit]("Verify an empty dependency cleanup report")

verifyClean := Def.uncached {
  val report = dependencyCleanup.value
  assert(report.redundantDependencies.isEmpty, report.toString)
  assert(report.tdepverSuggestions.isEmpty, report.toString)
  assert(
    report.lines == Vector("dependencyCleanup: no cleanup opportunities found"),
    report.lines.mkString("\n")
  )
}
