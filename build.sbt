ThisBuild / organization := "com.dwolla"
ThisBuild / homepage := Option(url("https://github.com/Dwolla/natchez-smithy4s"))
ThisBuild / tlBaseVersion := "0.1"
ThisBuild / crossScalaVersions := Seq("2.13.18", "3.3.9")
ThisBuild / githubWorkflowScalaVersions := Seq("2.13", "3")
// Scala Native warns when it links with clang older than 16. The ubuntu-22.04 runner image tops out at
// clang 15, and every clang on the ubuntu-24.04 image is 16 or newer.
ThisBuild / githubWorkflowOSes := Seq("ubuntu-24.04")
ThisBuild / tlJdkRelease := Option(8)
// Scala Native 0.5 warns that running it on a JDK older than 17 is deprecated. The published bytecode
// still targets Java 8 through tlJdkRelease.
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
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
ThisBuild / githubWorkflowBuildMatrixExclusions ++= Seq(
  // smithy4s publishes Scala Native artifacts for Scala 3 only, so every Native project here is
  // Scala 3 only, and sbt's `++ 2.13` leaves them all at Scala 3. The cell would only repeat the
  // Scala 3 Native build.
  MatrixExclude(Map(
    "scala" -> "2.13",
    "project" -> "rootNative",
  )),
)

val otel4sVersion = "1.1.0"

// Scala Native adds its compiler plugin with -Xplugin, which Scala 3's scaladoc ignores but warns about.
// Scala 2's scaladoc typechecks with the compiler plugins (e.g. kind-projector), so they stay there.
lazy val dropCompilerPluginsFromScala3Doc: Setting[Task[Seq[String]]] =
  Compile / doc / scalacOptions := {
    val options = (Compile / doc / scalacOptions).value
    if (scalaBinaryVersion.value == "3") options.filterNot(_.startsWith("-Xplugin")) else options
  }

// smithy4s publishes Scala Native artifacts for Scala 3 only
lazy val scala3OnlyNativeSettings: Seq[Def.Setting[?]] = Seq(
  crossScalaVersions := (ThisBuild / crossScalaVersions).value.filter(_.startsWith("3.")),
  scalaVersion := crossScalaVersions.value.head,
  dropCompilerPluginsFromScala3Doc,
  // Scala Native links without its optimizer (debug mode) by default. Unoptimized, the property-based
  // tests run close to munit-cats-effect's 30-second test timeout; optimized, they run 3-4x faster.
  // Only linked test binaries are affected: the published artifacts are NIR, which isn't optimized.
  nativeConfig ~= (_.withMode(scala.scalanative.build.Mode.releaseFast)),
)

// Scala Native artifacts are first published in 0.1.5, so MiMa must not look for earlier ones.
lazy val publishedNativeSettings: Seq[Def.Setting[?]] =
  scala3OnlyNativeSettings :+ (tlVersionIntroduced := Map("3" -> "0.1.5"))

lazy val `natchez-smithy4s` = crossProject(JVMPlatform, JSPlatform, NativePlatform)
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
        "org.tpolecat" %%% "natchez-core" % "0.3.10",
        "org.tpolecat" %%% "natchez-testkit" % "0.3.10" % Test,
        "org.scalameta" %%% "munit" % "1.3.6" % Test,
        "org.scalameta" %%% "munit-scalacheck" % "1.3.1" % Test,
        "org.typelevel" %%% "munit-cats-effect" % "2.2.1" % Test,
        "org.typelevel" %%% "scalacheck-effect" % "2.1.0" % Test,
        "org.typelevel" %%% "scalacheck-effect-munit" % "2.1.0" % Test,
      )
    },
  )
  .settings(Smithy4sCodegenPlugin.defaultSettings(Test))
  .settings(
    Test / smithy4sInputDirs := List(
      baseDirectory.value.getParentFile / "src" / "test" / "smithy",
    ),
  )
  .nativeSettings(publishedNativeSettings *)
  .enablePlugins(Smithy4sCodegenPlugin)
  .dependsOn(`testing-support` % Test)

lazy val `testing-support` = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("testing-support"))
  .settings(
    Compile / smithy4sInputDirs := List(
      baseDirectory.value.getParentFile / "src" / "main" / "smithy",
    ),
    libraryDependencies ++= {
      Seq(
        "com.disneystreaming.smithy4s" %%% "smithy4s-core" % smithy4sVersion.value,
        "org.scalacheck" %%% "scalacheck" % "1.20.0",
      )
    },
  )
  .nativeSettings(scala3OnlyNativeSettings *)
  .enablePlugins(Smithy4sCodegenPlugin, NoPublishPlugin)

lazy val `otel4s-smithy4s-metrics` = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("otel4s-metrics"))
  .settings(
    tlVersionIntroduced := Map("2.13" -> "0.1.3", "3" -> "0.1.3"),
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "com.dwolla.metrics.smithy",
    buildInfoOptions += BuildInfoOption.PackagePrivate,
    libraryDependencies ++= {
      Seq(
        "com.disneystreaming.smithy4s" %%% "smithy4s-core" % smithy4sVersion.value,
        "org.typelevel" %%% "otel4s-core-metrics" % otel4sVersion,
        "org.typelevel" %%% "otel4s-semconv-metrics-experimental" % otel4sVersion % Test,
        "org.typelevel" %%% "otel4s-sdk-metrics-testkit" % "0.19.5" % Test,
        "org.typelevel" %%% "cats-mtl" % "1.7.0" % Test,
        "org.typelevel" %%% "cats-effect-testkit" % "3.7.1" % Test,
        "org.scalameta" %%% "munit" % "1.3.6" % Test,
        "org.scalameta" %%% "munit-scalacheck" % "1.3.1" % Test,
        "org.typelevel" %%% "munit-cats-effect" % "2.2.1" % Test,
        "org.typelevel" %%% "scalacheck-effect" % "2.1.0" % Test,
        "org.typelevel" %%% "scalacheck-effect-munit" % "2.1.0" % Test,
      )
    },
  )
  .nativeSettings(publishedNativeSettings *)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(`testing-support` % Test)

lazy val `otel4s-smithy4s` = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("otel4s-tracing"))
  .settings(
    tlVersionIntroduced := Map("2.13" -> "0.1.4", "3" -> "0.1.4"),
    Compile / smithy4sInputDirs := List(
      baseDirectory.value.getParentFile / "src" / "main" / "smithy",
    ),
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "com.dwolla.tracing.smithy.otel4s",
    buildInfoOptions += BuildInfoOption.PackagePrivate,
    libraryDependencies ++= {
      Seq(
        "com.disneystreaming.smithy4s" %%% "smithy4s-core" % smithy4sVersion.value,
        "org.typelevel" %%% "otel4s-core-trace" % otel4sVersion,
        "org.typelevel" %%% "otel4s-semconv" % otel4sVersion,
        "com.dwolla" %%% "otel4s-tagless" % "0.2.8",
        "com.dwolla" %%% "tagless-core" % "0.2.8",
        "org.typelevel" %%% "otel4s-sdk-trace-testkit" % "0.19.5" % Test,
        "org.typelevel" %%% "cats-mtl" % "1.7.0" % Test,
        "org.scalameta" %%% "munit" % "1.3.6" % Test,
        "org.scalameta" %%% "munit-scalacheck" % "1.3.1" % Test,
        "org.typelevel" %%% "munit-cats-effect" % "2.2.1" % Test,
        "org.typelevel" %%% "scalacheck-effect" % "2.1.0" % Test,
        "org.typelevel" %%% "scalacheck-effect-munit" % "2.1.0" % Test,
      )
    },
  )
  .jvmSettings(
    libraryDependencies += "org.typelevel" %% "otel4s-oteljava-trace-testkit" % otel4sVersion % Test,
  )
  .nativeSettings(publishedNativeSettings *)
  .settings(Smithy4sCodegenPlugin.defaultSettings(Test))
  .settings(
    Test / smithy4sInputDirs := List(
      baseDirectory.value.getParentFile / "src" / "test" / "smithy",
    ),
  )
  .enablePlugins(Smithy4sCodegenPlugin, BuildInfoPlugin)
  .dependsOn(`testing-support` % Test)

lazy val `cross-redaction-tests` = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("cross-redaction-tests"))
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.3.6" % Test,
    ),
  )
  .settings(Smithy4sCodegenPlugin.defaultSettings(Test))
  .settings(
    Test / smithy4sInputDirs := List(
      baseDirectory.value.getParentFile / "src" / "test" / "smithy",
    ),
  )
  .nativeSettings(scala3OnlyNativeSettings *)
  .enablePlugins(Smithy4sCodegenPlugin, NoPublishPlugin)
  .dependsOn(`natchez-smithy4s`, `otel4s-smithy4s` % "compile->compile;test->test")

lazy val root = tlCrossRootProject
  .aggregate(`natchez-smithy4s`, `otel4s-smithy4s-metrics`, `otel4s-smithy4s`, `cross-redaction-tests`)
  .enablePlugins(NoPublishPlugin)
