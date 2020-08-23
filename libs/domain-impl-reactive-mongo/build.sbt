import Dependencies._

version := bearchVersion

libraryDependencies ++= Seq(
    "org.reactivemongo" %% "reactivemongo" % reactiveMongoVersion
  , "org.reactivemongo" %% "play2-reactivemongo" % s"${reactiveMongoVersion}-play26"
  , "org.reactivemongo" %% "reactivemongo-play-json-compat" % s"${reactiveMongoVersion}-play26"
  , "com.typesafe.play" %% "play-json" % playVersion
  , "com.typesafe" % "config" % typesafeConfigVersion
)
