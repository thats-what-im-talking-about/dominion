import Dependencies._

ThisBuild / version := dominionVersion
name := "dominion"

publishMavenStyle := false

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")

scalaVersion := scalaVsn

lazy val api = project in file("api")

lazy val `reactive-mongo-impl` = (project in file("libs/reactive-mongo-impl"))
  .dependsOn(api)
  .aggregate(api)

lazy val root = (project in file("."))
  .dependsOn(api, `reactive-mongo-impl`)
  .aggregate(api, `reactive-mongo-impl`)


root / publish / skip := true

//
//          S   O   N   A   T   Y   P   E  
//
//      P   U   B   L   I   S   H   I   N   G
//

ThisBuild / organization := "io.github.thats-what-im-talking-about"
ThisBuild / organizationName := "TWITA"
ThisBuild / organizationHomepage := Some(url("http://gihub.com/thats-what-im-talking-about"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/thats-what-im-talking-about/dominion"),
    "scm:git@github.com:thats-what-im-talking-about/dominion.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "bplawler",
    name  = "Brian Lawler",
    email = "bplawler@gmail.com",
    url   = url("https://github.com/bplawler")
  )
)

ThisBuild / description := "Scala-based Domain Object framework."
ThisBuild / licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage := Some(url("https://github.com/thats-what-im-talking-about/dominion"))

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true

