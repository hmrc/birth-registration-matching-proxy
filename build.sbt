import uk.gov.hmrc.DefaultBuildSettings.*
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

ThisBuild / scalaVersion := "2.13.16"
ThisBuild / majorVersion := 1

lazy val microservice = Project("birth-registration-matching-proxy", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .settings(
    scalaSettings,
    defaultSettings(),
    PlayKeys.playDefaultPort := 9006,
    libraryDependencies ++= AppDependencies(),
    scalacOptions ++= Seq("-feature", "-Wconf:cat=unused-imports&src=routes/.*:s")
  )
  .settings(CodeCoverageSettings())

addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt Test/scalafmt")
