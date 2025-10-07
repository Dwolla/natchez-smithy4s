ThisBuild / organization := "com.dwolla"
ThisBuild / homepage := Option(url("https://github.com/Dwolla/natchez-smithy4s"))
ThisBuild / tlBaseVersion := "0.1"
ThisBuild / crossScalaVersions := Seq("2.13.17", "3.3.6")
ThisBuild / githubWorkflowScalaVersions := Seq("2.13", "3")
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
ThisBuild / mergifyRequiredJobs ++= Seq("validate-steward")
ThisBuild / mergifyStewardConfig ~= { _.map {
  _.withAuthor("dwolla-oss-scala-steward[bot]")
    .withMergeMinors(true)
}}
ThisBuild / tlCiReleaseBranches += "main"

lazy val `natchez-smithy4s` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    Compile / smithy4sInputDirs := List(
      baseDirectory.value.getParentFile / "src" / "main" / "smithy",
    ),
    libraryDependencies ++= {
      Seq(
        "com.disneystreaming.smithy4s" %%% "smithy4s-core" % smithy4sVersion.value,
        "com.disneystreaming.smithy4s" %%% "smithy4s-json" % smithy4sVersion.value,
        "org.tpolecat" %%% "natchez-core" % "0.3.8",
        "org.tpolecat" %%% "natchez-testkit" % "0.3.8" % Test,
        "org.scalameta" %%% "munit" % "1.2.0" % Test,
        "org.scalameta" %%% "munit-scalacheck" % "1.2.0" % Test,
        "org.typelevel" %%% "munit-cats-effect" % "2.1.0" % Test,
        "org.typelevel" %%% "scalacheck-effect" % "2.0.0-M2" % Test,
        "org.typelevel" %%% "scalacheck-effect-munit" % "2.0.0-M2" % Test,
      )
    },
  )
  .enablePlugins(Smithy4sCodegenPlugin)
  .dependsOn(`testing-support` % Test)

lazy val `testing-support` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("testing-support"))
  .settings(
    Compile / smithy4sInputDirs := List(
      baseDirectory.value.getParentFile / "src" / "main" / "smithy",
    ),
    libraryDependencies ++= {
      Seq(
        "com.disneystreaming.smithy4s" %%% "smithy4s-core" % smithy4sVersion.value,
      )
    },
  )
  .enablePlugins(Smithy4sCodegenPlugin, NoPublishPlugin)

lazy val root = tlCrossRootProject
  .aggregate(`natchez-smithy4s`)
  .enablePlugins(NoPublishPlugin)
