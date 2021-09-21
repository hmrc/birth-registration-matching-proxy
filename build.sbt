import play.routes.compiler.InjectedRoutesGenerator
import play.sbt.routes.RoutesKeys.routesGenerator
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

lazy val appName: String = "birth-registration-matching-proxy"
val silencerVersion = "1.7.1"

resolvers += Resolver.jcenterRepo

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
libraryDependencies ++= Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-backend-play-28" % "5.14.0",
  "uk.gov.hmrc" %% "domain" % "6.2.0-play-28",
  compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
  "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full,

  // Test Dependencies
  "uk.gov.hmrc" %% "bootstrap-test-play-28" % "5.14.0" % Test,
  "org.scalatestplus" %% "mockito-3-4" % "3.2.9.0" % Test,
  "org.specs2" %% "specs2-core" % "4.12.12" % Test,
  "org.specs2" %% "specs2-mock" % "4.12.12" % Test
)

scalacOptions ++= Seq(
  "-P:silencer:pathFilters=views;routes"
)
