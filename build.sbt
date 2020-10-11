import Dependencies._

ThisBuild / organization := "twita"
ThisBuild / version := dominionVersion
name := "dominion"

publishMavenStyle := false

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")

lazy val api = project in file("api")

lazy val `reactive-mongo-impl` = (project in file("libs/reactive-mongo-impl"))
  .dependsOn(api)
  .aggregate(api)

lazy val root = (project in file("."))
  .dependsOn(api, `reactive-mongo-impl`)
  .aggregate(api, `reactive-mongo-impl`)
