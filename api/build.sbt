import Dependencies._

name := "dominion-api"

libraryDependencies ++= Seq(
    "com.beachape" %% "enumeratum" % enumeratumVersion
  , "com.beachape" %% "enumeratum-play-json" % enumeratumVersion
  , "com.typesafe.akka" %% "akka-stream" % akkaVersion
)
