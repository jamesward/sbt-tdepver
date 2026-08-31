scalaVersion := "3.8.4"

libraryDependencies ++= Seq(
  "com.jamesward" %% "zio-http-guard" % "0.0.2",
  "dev.zio" %% "zio-http" % "3.11.1",
  "dev.zio" %% "zio-http-testkit" % "3.11.4" % Test,
  "dev.zio" %% "zio-streams-compress-gzip" % "1.1.4",
  "dev.zio" %% "zio-streams-compress-tar" % "1.1.4",
  "dev.zio" %% "zio-streams-compress-zip" % "1.1.4",
  "dev.zio" %% "zio-test-sbt" % ("dev.zio" %% "zio").version % Test,
)

val verifyCleanup = taskKey[Unit]("Verify dependency cleanup findings")

verifyCleanup := Def.uncached {
  val report = dependencyCleanup.value

  val redundantHttp = report.redundantDependencies.find(value =>
    value.organization == "dev.zio" && value.name == "zio-http"
  ).getOrElse(sys.error(s"Missing redundant zio-http finding: $report"))
  assert(
    redundantHttp.declaration == "\"dev.zio\" %% \"zio-http\" % \"3.11.1\"",
    redundantHttp.declaration
  )

  val testkit = report.tdepverSuggestions.find(value =>
    value.organization == "dev.zio" && value.name == "zio-http-testkit"
  ).getOrElse(sys.error(s"Missing zio-http-testkit suggestion: $report"))
  assert(testkit.sourceOrganization == "dev.zio", testkit.toString)
  assert(testkit.sourceName == "zio-http", testkit.toString)
  assert(
    testkit.currentDeclaration ==
      "\"dev.zio\" %% \"zio-http-testkit\" % \"3.11.4\" % Test",
    testkit.currentDeclaration
  )
  assert(
    testkit.replacement ==
      "\"dev.zio\" %% \"zio-http-testkit\" % (\"dev.zio\" %% \"zio-http\").version % Test",
    testkit.replacement
  )

  assert(
    !report.tdepverSuggestions.exists(_.name == "zio-test-sbt"),
    "Already-deferred dependencies must not be suggested again"
  )

  assert(
    !report.tdepverSuggestions.exists(_.name.startsWith("zio-streams-compress-")),
    "Suggestions must remain valid when all replacements are applied together"
  )
}
