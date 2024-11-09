import Dependencies.Versions.akkaHttp
import sbt.*

object Dependencies {

  object Versions {
    val cats       = "2.6.1"
    val catsEffect = "2.5.1"
    val fs2        = "2.5.4"
    val http4s     = "0.22.15"
    val circe      = "0.14.2"
    val pureConfig = "0.17.4"
    val akkaHttp   = "10.2.10"
    val akkaStream = "2.6.20"
    val akkaSlf4j  = "2.6.20"
    val slf4jApi   = "2.0.16"
    val sqliteJdbc = "3.39.3.0"
    val kindProjector  = "0.13.3"
    val logback        = "1.5.6"
    val scalaCheck     = "1.15.3"
    val scalaTest      = "3.2.7"
    val catsScalaCheck = "0.3.2"

  }

  object Libraries {
    def circe(artifact: String): ModuleID  = "io.circe"   %% artifact % Versions.circe
    def http4s(artifact: String): ModuleID = "org.http4s" %% artifact % Versions.http4s

    lazy val cats       = "org.typelevel" %% "cats-core"   % Versions.cats
    lazy val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
    lazy val fs2        = "co.fs2"        %% "fs2-core"    % Versions.fs2

    lazy val http4sDsl       = http4s("http4s-dsl")
    lazy val http4sServer    = http4s("http4s-blaze-server")
    lazy val http4sClient     = http4s("http4s-blaze-client") // Add this line for the HTTP client
    lazy val http4sCirce     = http4s("http4s-circe")
    lazy val circeCore       = circe("circe-core")
    lazy val circeGeneric    = circe("circe-generic")
    lazy val circeGenericExt = circe("circe-generic-extras")
    lazy val circeParser     = circe("circe-parser")
    lazy val pureConfig      = "com.github.pureconfig" %% "pureconfig" % Versions.pureConfig

    lazy val akkaHttp = "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp
    lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % Versions.akkaStream
    lazy val akkaSlf4f = "com.typesafe.akka" %% "akka-slf4j" % Versions.akkaSlf4j

    lazy val slf4fApi =  "org.slf4j" %% "slf4j-api" % Versions.slf4jApi

    // Compiler plugins
    lazy val kindProjector = "org.typelevel" %% "kind-projector" % Versions.kindProjector cross CrossVersion.full

    // Runtime
    lazy val logback = "ch.qos.logback" % "logback-classic" % Versions.logback

    lazy val sqliteJdbc = "org.xerial" % "sqlite-jdbc" % Versions.sqliteJdbc

    // Test
    lazy val scalaTest      = "org.scalatest"     %% "scalatest"       % Versions.scalaTest
    lazy val scalaCheck     = "org.scalacheck"    %% "scalacheck"      % Versions.scalaCheck
    lazy val catsScalaCheck = "io.chrisdavenport" %% "cats-scalacheck" % Versions.catsScalaCheck
  }

}