scalaVersion := "3.8.4"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio-streams" % "2.1.26",
  "dev.zio" %% "zio-test" %
    ("dev.zio" %% "not-in-the-graph").version % Test,
)
