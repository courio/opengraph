name := "opengraph"
ThisBuild / organization := "org.courio"
ThisBuild / version := "1.1.0"
ThisBuild / scalaVersion := "2.13.11"

ThisBuild / resolvers ++= Seq(
  Resolver.sonatypeOssRepos("releases"),
  Resolver.sonatypeOssRepos("snapshots")
).flatten
ThisBuild / scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

ThisBuild / publishTo := sonatypePublishToBundle.value
ThisBuild / sonatypeProfileName := "org.courio"
publishMavenStyle := true
ThisBuild / licenses := Seq("MIT" -> url("https://github.com/courio/core/blob/master/LICENSE"))
ThisBuild / sonatypeProjectHosting := Some(xerial.sbt.Sonatype.GitHubHosting("courio", "opengraph", "contact@courio.com"))
ThisBuild / homepage := Some(url("https://courio.com"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/courio/opengraph"),
    "scm:git@github.com:courio/opengraph.git"
  )
)
ThisBuild / developers := List(
  Developer(id="darkfrog", name="Matt Hicks", email="matt@matthicks.com", url=url("http://matthicks.com"))
)

val spiceVersion = "0.0.35"
val jSoupVersion = "1.16.1"
val media4sVersion = "1.0.19"

fork := true

libraryDependencies ++= Seq(
  "com.outr" %% "spice-client-okhttp" % spiceVersion,
  "com.outr" %% "media4s" % media4sVersion,
  "org.jsoup" % "jsoup" % jSoupVersion
)