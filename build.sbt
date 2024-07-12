ThisBuild / organization := "com.dwolla"
ThisBuild / homepage := Option(url("https://github.com/Dwolla/natchez-smithy4s"))
ThisBuild / tlBaseVersion := "0.1"
ThisBuild / crossScalaVersions := Seq("2.12.19", "2.13.14", "3.3.3")
ThisBuild / githubWorkflowScalaVersions := Seq("2.12", "2.13", "3")
ThisBuild / tlJdkRelease := Option(8)
ThisBuild / tlFatalWarnings := githubIsWorkflowBuild.value
ThisBuild / startYear := Option(2024)
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  Developer(
    "bpholt",
    "Brian Holt",
    "bholt+natchez-smithy@dwolla.com",
    url("https://dwolla.com")
  ),
)
ThisBuild / tlSonatypeUseLegacyHost := true
ThisBuild / mergifyRequiredJobs ++= Seq("validate-steward")
ThisBuild / mergifyStewardConfig ~= { _.map {
  _.withAuthor("dwolla-oss-scala-steward[bot]")
    .withMergeMinors(true)
}}

lazy val `natchez-smithy4s` = project
  .in(file("."))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    libraryDependencies ++= {
      Seq(
        "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion.value,
        "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % smithy4sVersion.value,
        "com.disneystreaming.smithy4s" %% "smithy4s-cats" % smithy4sVersion.value,
        "com.disneystreaming.smithy4s" %% "smithy4s-json" % smithy4sVersion.value,
        "org.tpolecat" %% "natchez-core" % "0.3.5",
      )
    },
  )
