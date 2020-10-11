import sbt._

/**
 * This file contains the versions of our various dependencies that we need to share
 * across all of our builds.  For additional background and documentation on what
 * may be included here, see:
 *
 * https://www.scala-sbt.org/1.x/docs/Organizing-Build.html
 */
object Dependencies {

  lazy val organization = "twita"
  lazy val dominionVersion = "0.1"

  // ---------------------------------------------------------------------- 
  //
  //        V   E   R   S   I   O   N   S
  //
  // ---------------------------------------------------------------------- 
  lazy val scalaVersion = "2.12.8"
  lazy val reactiveMongoVersion = "0.20.11"
  lazy val typesafeConfigVersion = "1.4.0"
  lazy val playVersion = "2.6.13"
  lazy val enumeratumVersion = "1.5.13"
}
