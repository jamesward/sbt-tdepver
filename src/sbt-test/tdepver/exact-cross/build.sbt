scalaVersion := "3.8.4"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio-streams" % "2.1.25",
  "dev.zio" %% "zio-http" % "3.11.3",
  "dev.zio" %% "zio-test" %
    ("dev.zio" %% "zio").version % Test,
)

val verifyResolution = taskKey[Unit]("Verify the deferred dependency resolution")

verifyResolution := {
  val report = update.value

  def selectedRevision(moduleName: String): String = {
    val revisions = report.configurations
      .flatMap(_.modules)
      .filter(module => !module.evicted && module.module.name == moduleName)
      .map(_.module.revision)
      .distinct
    revisions match {
      case Vector(revision) => revision
      case other => sys.error(s"Expected one selected revision for $moduleName, found $other")
    }
  }

  val zioRevision = selectedRevision("zio_3")
  val zioTestRevision = selectedRevision("zio-test_3")
  assert(zioRevision == "2.1.26", s"Expected transitive selection 2.1.26, found $zioRevision")
  assert(zioTestRevision == zioRevision, s"zio-test $zioTestRevision != zio $zioRevision")

  val compileModules = report.configurations
    .find(_.configuration.name == "compile")
    .toVector
    .flatMap(_.modules)
    .map(_.module.name)
  val testModules = report.configurations
    .find(_.configuration.name == "test")
    .toVector
    .flatMap(_.modules)
    .map(_.module.name)

  assert(!compileModules.contains("zio-test_3"), "deferred Test dependency leaked into Compile")
  assert(testModules.contains("zio-test_3"), "deferred dependency is absent from Test")
  assert(!libraryDependencies.value.exists(module =>
    module.organization == "dev.zio" && module.name == "zio"
  ), "source dependency must remain transitive")
}
