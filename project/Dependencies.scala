import sbt._

/**
  * This file contains the versions of our various dependencies that we need to share
  * across all of our builds.  For additional background and documentation on what
  * may be included here, see:
  *
  * https://www.scala-sbt.org/1.x/docs/Organizing-Build.html
  */
object Dependencies {
  lazy val dominionVersion = "0.1.0"
  // ----------------------------------------------------------------------
  //
  //        V   E   R   S   I   O   N   S
  //
  // ----------------------------------------------------------------------
  lazy val scalaVsn = "2.13.2"
  lazy val reactiveMongoVersion = "0.20.11"
  lazy val typesafeConfigVersion = "1.4.0"
  lazy val playVersion = "2.7.4"
  lazy val enumeratumVersion = "1.5.13"
  lazy val enumeratumJsonVersion = "1.5.16"
  lazy val akkaVersion = "2.6.10"
}
