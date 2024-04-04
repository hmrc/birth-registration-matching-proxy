import play.routes.compiler.InjectedRoutesGenerator
import play.sbt.routes.RoutesKeys.routesGenerator
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.*
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

val appName = "birth-registration-matching-proxy"

lazy val scoverageSettings: Seq[Def.Setting[?]] =
  Seq(
    ScoverageKeys.coverageExcludedPackages :=
      "<empty>;.*CustomWSConfigParser;.*OptionalAhcHttpCacheProvider;.*AhcHttpCacheParser;testOnlyDoNotUseInAppConf.*;" +
        "uk.gov.hmrc.brm.views.*;prod.*;uk.gov.hmrc.BuildInfo.*;app.Routes.*;",
    ScoverageKeys.coverageMinimumStmtTotal := 100,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .settings(
    scoverageSettings,
    scalaSettings,
    scalaVersion := "2.13.13",
//    libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always,
    defaultSettings(),
    majorVersion := 1,
    PlayKeys.playDefaultPort := 9006,
    libraryDependencies ++= AppDependencies(),
    scalacOptions += "-Wconf:src=routes/.*:s",
    retrieveManaged := true,
    routesGenerator := InjectedRoutesGenerator
  )

addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt Test/scalafmt")
addCommandAlias("scalastyleAll", "all scalastyle Test/scalastyle")
