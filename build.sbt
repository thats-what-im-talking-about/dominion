name := "be-arch"
organization := "twita"
version := "0.1"
scalaVersion := "2.12.8"

publishMavenStyle := false

libraryDependencies ++= Seq(
    "com.beachape" %% "enumeratum" % "1.5.13"
  , "com.beachape" %% "enumeratum-play-json" % "1.5.13"
)
