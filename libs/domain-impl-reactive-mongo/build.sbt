import Dependencies._

version := bearchVersion

libraryDependencies ++= Seq(
    "org.reactivemongo" %% "reactivemongo" % reactiveMongoVersion
  , "org.reactivemongo" %% "play2-reactivemongo" % s"${reactiveMongoVersion}-play26"
  , "org.reactivemongo" %% "reactivemongo-play-json" % s"${reactiveMongoVersion}-play26"
  , "com.typesafe.play" %% "play-json" % playVersion
)
