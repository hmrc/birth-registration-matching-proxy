import sbt._
import uk.gov.hmrc.{SbtArtifactory, SbtAutoBuildPlugin}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

object MicroServiceBuild extends Build with MicroService {

  val appName = "birth-registration-matching-proxy"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()

  override lazy val plugins: Seq[Plugins] = Seq(
    SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory
  )
}

private object AppDependencies {
  import play.core.PlayVersion

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-play-26" % "1.3.0",
    "uk.gov.hmrc" %% "domain" % "5.6.0-play-26"
  )

  lazy val scope: String = "test"

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26" % "test",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "org.pegdown" % "pegdown" % "1.6.0" % "test",
    "com.typesafe.play" %% "play-test" % PlayVersion.current % "test",
    "org.mockito" % "mockito-core" % "3.2.4",
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1",
    "org.specs2" % "specs2_2.10" % "2.3.13"
    )

  def apply(): Seq[ModuleID] = compile ++ test
}
