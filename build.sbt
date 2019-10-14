import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

name in ThisBuild := "opengraph"
organization in ThisBuild := "org.courio"
version in ThisBuild := "1.0.0-SNAPSHOT"
scalaVersion in ThisBuild := "2.13.1"

resolvers in ThisBuild ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)
scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature")

publishTo in ThisBuild := sonatypePublishToBundle.value
sonatypeProfileName in ThisBuild := "org.courio"
publishMavenStyle in ThisBuild := true
licenses in ThisBuild := Seq("MIT" -> url("https://github.com/courio/core/blob/master/LICENSE"))
sonatypeProjectHosting in ThisBuild := Some(xerial.sbt.Sonatype.GitHubHosting("courio", "opengraph", "contact@courio.com"))
homepage in ThisBuild := Some(url("https://courio.com"))
scmInfo in ThisBuild := Some(
  ScmInfo(
    url("https://github.com/courio/opengraph"),
    "scm:git@github.com:courio/opengraph.git"
  )
)
developers in ThisBuild := List(
  Developer(id="darkfrog", name="Matt Hicks", email="matt@matthicks.com", url=url("http://matthicks.com"))
)

val youiVersion = "0.11.32"
val jSoupVersion = "1.12.1"

lazy val root = project.in(file("."))
  .aggregate(coreJS, coreJVM)
  .settings(
    publish := {},
    publishLocal := {}
  )

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "opengraph",
    libraryDependencies ++= Seq(
      "io.youi" %%% "youi-client" % youiVersion,
      "org.jsoup" % "jsoup" % jSoupVersion
    )
  )
  .jvmSettings(
    fork := true
  )

lazy val coreJS = core.js
lazy val coreJVM = core.jvm
