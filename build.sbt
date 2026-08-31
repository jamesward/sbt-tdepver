sbtPlugin := true
enablePlugins(SbtPlugin)

organization := "com.jamesward"
name := "sbt-tdepver"
homepage := Some(uri("https://github.com/jamesward/sbt-tdepver"))
licenses := List("Apache-2.0" -> uri("https://www.apache.org/licenses/LICENSE-2.0"))

developers := List(
  Developer(
    "jamesward",
    "James Ward",
    "james@jamesward.com",
    uri("https://jamesward.com")
  )
)

versionScheme := Some("semver-spec")

javacOptions ++= Seq("-source", "17", "-target", "17")
scalacOptions ++= Seq("-release", "17", "-deprecation", "-feature", "-Werror")

libraryDependencies += "org.scalameta" %% "munit" % "1.3.5" % Test

scriptedLaunchOpts += s"-Dplugin.version=${version.value}"
scriptedBufferLog := false
