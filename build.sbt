name := "be-arch"
organization := "twita"
version := "0.1"
scalaVersion := "2.12.8"

publishMavenStyle := false

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")

lazy val `domain-api` = project in file("domain-api")
