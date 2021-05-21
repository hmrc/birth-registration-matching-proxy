import play.core.PlayVersion
import play.routes.compiler.InjectedRoutesGenerator
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import scoverage.ScoverageKeys
import play.sbt.routes.RoutesKeys.routesGenerator
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

lazy val appName: String = "birth-registration-matching-proxy"
val silencerVersion = "1.7.1"

resolvers += Resolver.jcenterRepo

lazy val appDependencies : Seq[ModuleID] = compile ++ test

lazy val scoverageSettings = {
  Seq(
    ScoverageKeys.coverageExcludedPackages :=
      "<empty>;.*CustomWSConfigParser;.*OptionalAhcHttpCacheProvider;.*AhcHttpCacheParser;testOnlyDoNotUseInAppConf.*;uk.gov.hmrc.brm.views.*;prod.*;uk.gov.hmrc.BuildInfo.*;app.Routes.*;",
    ScoverageKeys.coverageMinimum := 100,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}

lazy val microservice = Project(appName, file("."))
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)

scoverageSettings
scalaSettings
publishingSettings
defaultSettings()

majorVersion := 1
scalaVersion := "2.12.12"
evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
PlayKeys.playDefaultPort := 9006

retrieveManaged := true
routesGenerator := InjectedRoutesGenerator


enablePlugins(PlayScala, SbtDistributablesPlugin)
libraryDependencies ++= appDependencies


scalacOptions ++= Seq(
  "-P:silencer:pathFilters=views;routes"
)

lazy val compile = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-backend-play-27" % "5.3.0",
  "uk.gov.hmrc" %% "domain" % "5.11.0-play-27",
  compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
  "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
)

lazy val scope: String = "test"

lazy val test: Seq[ModuleID] = Seq(
  "org.scalatest" %% "scalatest" % "3.0.9" % scope,
  "org.pegdown" % "pegdown" % "1.6.0" % scope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
  "org.mockito" % "mockito-core" % "3.10.0" % scope,
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % scope,
  "org.specs2" %% "specs2-core" % "4.5.1" % scope,
  "org.specs2" %% "specs2-mock" % "4.5.1" % scope
)


