name := "opengraph"
organization in ThisBuild := "org.courio"
version in ThisBuild := "1.0.4-SNAPSHOT"
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

val youiVersion = "0.13.0"
val jSoupVersion = "1.12.1"
val media4sVersion = "1.0.14"

fork := true

libraryDependencies ++= Seq(
  "io.youi" %% "youi-client" % youiVersion,
  "io.youi" %% "youi-spatial" % youiVersion,
  "org.matthicks" %% "media4s" % media4sVersion,
  "org.jsoup" % "jsoup" % jSoupVersion
)