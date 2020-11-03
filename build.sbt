val silencerVersion = "1.7.1"
val kamonVersion = "2.1.6"

val kamonTestkitDep = "io.kamon" %% "kamon-testkit" % kamonVersion
val scalatestDep = "org.scalatest" %% "scalatest" % "3.2.0"

inThisBuild(
  List(
    organization := "com.github.jatcwang",
    homepage := Some(url("https://github.com/jatcwang/kamon-cats-effect")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jatcwang",
        "Jacob Wang",
        "jatcwang@gmail.com",
        url("https://almostfunctional.com"),
      ),
    ),
  ),
)

// fixme docs for setting up everything, and explain cats-effect works because of kamon-executors
lazy val `cats-effect` = Project("cats-effect", file("modules/cats-effect"))
  .settings(commonSettings)
  .settings(
    name := "kamon-cats-effect",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.2.0",
      "org.typelevel" %% "cats-effect" % "2.1.4",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6",
      "io.kamon" %% "kamon-core" % kamonVersion,
      "io.kamon" %% "kamon-cats-io" % kamonVersion,
      "io.kamon" %% "kamon-executors" % kamonVersion,
    ),
  )

lazy val `cats-effect-test` = Project("cats-effect-test", file("modules/cats-effect-test"))
  .dependsOn(`cats-effect`)
  .enablePlugins(JavaAgent)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    javaAgents += KanelaAgent,
    libraryDependencies ++= Seq(
      kamonTestkitDep,
      scalatestDep,
    ).map(_ % Test),
  )

lazy val fs2 = Project("fs2", file("modules/fs2"))
  .dependsOn(`cats-effect`)
  .settings(commonSettings)
  .settings(
    name := "kamon-fs2",
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % "2.4.4",
    ),
  )

lazy val `fs2-test` = Project("fs2-test", file("modules/fs2-test"))
  .dependsOn(fs2, `cats-effect-test` % "test->test")
  .enablePlugins(JavaAgent)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    javaAgents += KanelaAgent,
    libraryDependencies ++= Seq(
      kamonTestkitDep,
      scalatestDep,
    ).map(_ % Test),
  )

lazy val docs = project
  .dependsOn(`cats-effect`, `cats-effect-test`)
  .enablePlugins(MicrositesPlugin)
  .settings(
    commonSettings,
    publish / skip := true,
  )
  .settings(
    mdocIn := file("docs/docs"),
    mdocExtraArguments ++= Seq("--noLinkHygiene"),
    micrositeName := "Kamon Cats-Effect",
    micrositeDescription := "Utilities for integrating Kamon, Cats Effect and FS2 ",
    micrositeUrl := "https://jatcwang.github.io",
    micrositeBaseUrl := "/kamon-cats-effect",
    micrositeDocumentationUrl := s"${micrositeBaseUrl.value}/docs/fixme",
    micrositeAuthor := "Jacob Wang",
    micrositeGithubOwner := "jatcwang",
    micrositeGithubRepo := "kamon-cats-effect",
    micrositeCompilingDocsTool := WithMdoc,
    micrositeHighlightTheme := "a11y-light",
    micrositePushSiteWith := GitHub4s,
    micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
  )

lazy val root = project
  .in(new File("."))
  .aggregate(`cats-effect`, `cats-effect-test`, docs)
  .settings(noPublishSettings)
  .settings(commonSettings)

val scala213 = "2.13.3"
val scala212 = "2.12.12"
lazy val commonSettings = Seq(
  scalaVersion := scala212,
  crossScalaVersions := Seq(scala213, scala212),
  scalacOptions ++= Seq(
    "-Ywarn-macros:after",
  ),
  doc / scalacOptions --= Seq("-Xfatal-warnings"),
  scalacOptions --= {
    if (sys.env.contains("CI"))
      Seq.empty
    else
      Seq("-Xfatal-warnings")
  },
  Test / scalacOptions --= Seq(
    "-Ywarn-value-discard",
  ),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  libraryDependencies ++= Seq(
    compilerPlugin(
      "com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full,
    ),
    "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full,
  ),
)

lazy val noPublishSettings = Seq(
  publish / skip := true,
)

lazy val KanelaAgent: ModuleID = "io.kamon" % "kanela-agent" % "1.0.6" % "test"
