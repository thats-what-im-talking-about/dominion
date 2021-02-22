import Dependencies._

scalaVersion := scalaVsn
version := dominionVersion
name := "dominion-reactive-mongo-impl"

libraryDependencies ++= Seq(
  "org.reactivemongo" %% "reactivemongo" % reactiveMongoVersion,
  "org.reactivemongo" %% "play2-reactivemongo" % s"${reactiveMongoVersion}-play27",
  "org.reactivemongo" %% "reactivemongo-play-json-compat" % s"${reactiveMongoVersion}-play27",
  "org.reactivemongo" %% "reactivemongo-akkastream" % reactiveMongoVersion,
  "com.typesafe.play" %% "play-json" % playVersion,
  "org.scalactic" %% "scalactic" % "3.2.0",
  "org.scalatest" %% "scalatest" % "3.2.0" % "test",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.30" % "test"
)
