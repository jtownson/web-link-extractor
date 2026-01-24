ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.8.0"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.scalacheck" %% "scalacheck" % "1.19.0" % Test,
  "org.typelevel" %% "cats-effect-testing-scalatest" % "1.7.0" % Test,
  "org.typelevel" %% "cats-effect" % "3.6.3",
  "co.fs2" %% "fs2-core" % "3.12.2",
  "org.jsoup" % "jsoup" % "1.22.1",
  "commons-io" % "commons-io" % "2.21.0"
)

lazy val root = (project in file("."))
  .settings(
    scalacOptions := Seq(
      "-deprecation",
      "-unchecked",
      "-Wnonunit-statement"),
    name := "web-link-extractor"
  )
