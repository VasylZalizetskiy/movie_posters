import Dependencies._
import sbt.Keys._
import sbt.Resolver

name := "movie_posters"

lazy val commonSettings = Seq(
  version := (version in ThisBuild).value,
  scalaVersion := "2.12.5"
)

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, SbtNativePackager)
  .aggregate(registration)
  .dependsOn(registration)
  .settings(commonSettings: _*)

lazy val registration = project.settings(commonSettings: _*)

resolvers += Resolver.bintrayRepo("sergkh", "maven")
resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  ehcache,
  filters,
  guice,
  ws,
  "com.mohiva"        %% "play-silhouette"                  % silhouetteVersion,
  "com.mohiva"        %% "play-silhouette-password-bcrypt"  % silhouetteVersion,
  "com.mohiva"        %% "play-silhouette-persistence"      % silhouetteVersion,
  "com.mohiva"        %% "play-silhouette-crypto-jca"       % silhouetteVersion,
  "com.impactua"      %% "play2-auth"                       % authVersion,
  "com.impactua"      %% "kafka-restartable"                % kafkaRestartableVersion,
  "com.impactua"      %% "redis-scala"                      % redisVersion,
  "net.codingwell"    %% "scala-guice"                      % guiceVersion,
  "org.webjars"       %  "swagger-ui"                       % "3.13.3",
  "com.iheart"        %% "play-swagger"                     % "0.7.4",
  "com.iheart"        %% "ficus"                            % "1.4.3",
  "org.webjars"       %% "webjars-play"                     % "2.6.3",
  "net.ruippeixotog"  %% "scala-scraper"                    % "2.1.0",
  "org.reactivemongo" %% "play2-reactivemongo"              % "0.13.0-play26",
  "com.twitter"       %% "chill"                            % "0.9.2"
)
//pipelineStages := Seq(rjs)

routesGenerator := InjectedRoutesGenerator

scalacOptions in ThisBuild ++= Seq("-feature", "-language:postfixOps","-Xmax-classfile-name","78")