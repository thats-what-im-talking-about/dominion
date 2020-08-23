import Dependencies._

name := "be-arch"
version := bearchVersion

publishMavenStyle := false

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")

lazy val `domain-api` = project in file("domain-api")

lazy val `domain-impl-reactive-mongo` = (project in file("libs/domain-impl-reactive-mongo"))
  .dependsOn(`domain-api`)

lazy val root = (project in file("."))
  .dependsOn(`domain-api`, `domain-impl-reactive-mongo`)
