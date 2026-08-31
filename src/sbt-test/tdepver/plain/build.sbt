scalaVersion := "3.8.4"
libraryDependencies ++= Seq(
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.17.3",
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names" %
    ("com.fasterxml.jackson.core" % "jackson-databind").version,
)

val verifyResolution = taskKey[Unit]("Verify plain deferred dependency resolution")

verifyResolution := {
  val report = update.value

  def selectedRevision(organization: String, moduleName: String): String = {
    val revisions = report.configurations
      .filter(_.configuration.name == "compile")
      .flatMap(_.modules)
      .filter(module =>
        !module.evicted &&
          module.module.organization == organization &&
          module.module.name == moduleName
      )
      .map(_.module.revision)
      .distinct
    revisions match {
      case Vector(revision) => revision
      case other => sys.error(s"Expected one selected revision for $organization:$moduleName, found $other")
    }
  }

  val databind = selectedRevision("com.fasterxml.jackson.core", "jackson-databind")
  val parameterNames = selectedRevision(
    "com.fasterxml.jackson.module",
    "jackson-module-parameter-names"
  )
  assert(databind == "2.17.3", s"Unexpected jackson-databind revision $databind")
  assert(parameterNames == databind, s"parameter-names $parameterNames != databind $databind")
}
