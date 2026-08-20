import sbt.Setting
import scoverage.ScoverageKeys.*

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    ".*Routes.*",
    // a copy of Play's own AhcWSModule, changed only to swap in CustomWSConfigParser; its uncovered code is
    // the HTTP cache path, which this service does not enable
    ".*CustomAhcWSModule.*"
  )

  private val settings: Seq[Setting[?]] = Seq(
    coverageExcludedFiles := excludedPackages.mkString(";"),
    coverageMinimumStmtTotal := 100,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )

  def apply(): Seq[Setting[?]] = settings

}
