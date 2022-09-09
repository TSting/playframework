/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */
import BuildSettings._
import Dependencies._
import Generators._
import interplay.PlayBuildBase.autoImport._
import pl.project13.scala.sbt.JmhPlugin.generateJmhSourcesAndResources
import sbt.Keys.parallelExecution
import sbt._
import sbt.io.Path._
import org.scalafmt.sbt.ScalafmtPlugin

// Customise sbt-dynver's behaviour to make it work with tags which aren't v-prefixed
(ThisBuild / dynverVTagPrefix) := false

// Sanity-check: assert that version comes from a tag (e.g. not a too-shallow clone)
// https://github.com/dwijnand/sbt-dynver/#sanity-checking-the-version
Global / onLoad := (Global / onLoad).value.andThen { s =>
  dynverAssertTagVersion.value
  s
}

lazy val BuildLinkProject = PlayNonCrossBuiltProject("Build-Link", "dev-mode/build-link")
  .settings(publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))))
  .dependsOn(PlayExceptionsProject)

// run-support project is only compiled against sbt scala version
lazy val RunSupportProject = PlaySbtProject("Run-Support", "dev-mode/run-support")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    target := target.value / "run-support",
    libraryDependencies ++= runSupportDependencies((pluginCrossBuild / sbtVersion).value)
  )
  .dependsOn(BuildLinkProject)

lazy val RoutesCompilerProject = PlayDevelopmentProject("Routes-Compiler", "dev-mode/routes-compiler")
  .enablePlugins(SbtTwirl)
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= routesCompilerDependencies(scalaVersion.value),
    TwirlKeys.templateFormats := Map("twirl" -> "play.routes.compiler.ScalaFormat")
  )

lazy val SbtRoutesCompilerProject = PlaySbtProject("Sbt-Routes-Compiler", "dev-mode/routes-compiler")
  .enablePlugins(SbtTwirl)
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    target := target.value / "sbt-routes-compiler",
    libraryDependencies ++= routesCompilerDependencies(scalaVersion.value),
    TwirlKeys.templateFormats := Map("twirl" -> "play.routes.compiler.ScalaFormat")
  )

lazy val StreamsProject = PlayCrossBuiltProject("Play-Streams", "core/play-streams")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= streamsDependencies
  )

lazy val PlayExceptionsProject = PlayNonCrossBuiltProject("Play-Exceptions", "core/play-exceptions").settings(publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))))

lazy val billOfMaterials = PlayCrossBuiltProject("bill-of-materials", "dev-mode/bill-of-materials")
  .enablePlugins(BillOfMaterialsPlugin)
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    name := "play-bom",
    bomIncludeProjects := userProjects,
    pomExtra := pomExtra.value :+ bomDependenciesListing.value,
    mimaPreviousArtifacts := Set.empty
  )

lazy val PlayJodaFormsProject = PlayCrossBuiltProject("Play-Joda-Forms", "web/play-joda-forms")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= joda
  )
  .dependsOn(PlayProject, PlaySpecs2Project % "test")

lazy val PlayProject = PlayCrossBuiltProject("Play", "core/play")
  .enablePlugins(SbtTwirl)
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= runtime(scalaVersion.value) ++ scalacheckDependencies ++ cookieEncodingDependencies :+
      jimfs % Test,
    (Compile / sourceGenerators) += Def
      .task(
        PlayVersion(
          version.value,
          scalaVersion.value,
          sbtVersion.value,
          Dependencies.akkaVersion,
          Dependencies.akkaHttpVersion,
          (Compile / sourceManaged).value
        )
      )
      .taskValue,
    (Compile / TwirlKeys.compileTemplates / sourceDirectories) := (Compile / unmanagedSourceDirectories).value,
    TwirlKeys.templateImports += "play.api.templates.PlayMagic._",
    (Compile / packageSrc / mappings) ++= {
      // Add both the templates, useful for end users to read, and the Scala sources that they get compiled to,
      // so omnidoc can compile and produce scaladocs for them.
      val twirlSources = (Compile / TwirlKeys.compileTemplates / sources).value
        .pair(relativeTo((Compile / TwirlKeys.compileTemplates / sourceDirectories).value))

      val twirlTarget = (Compile / TwirlKeys.compileTemplates / target).value
      // The pair with errorIfNone being false both creates the mappings, and filters non twirl outputs out of
      // managed sources
      val twirlCompiledSources = (Compile / managedSources).value.pair(relativeTo(twirlTarget), errorIfNone = false)

      twirlSources ++ twirlCompiledSources
    },
    Docs.apiDocsIncludeManaged := true
  )
  .settings(
    Docs.playdocSettings: _*
  )
  .dependsOn(
    BuildLinkProject,
    StreamsProject
  )

lazy val PlayServerProject = PlayCrossBuiltProject("Play-Server", "transport/server/play-server")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= playServerDependencies
  )
  .dependsOn(
    PlayProject,
    PlayGuiceProject % "test"
  )

lazy val PlayNettyServerProject = PlayCrossBuiltProject("Play-Netty-Server", "transport/server/play-netty-server")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= netty
  )
  .dependsOn(PlayServerProject)

lazy val PlayAkkaHttpServerProject =
  PlayCrossBuiltProject("Play-Akka-Http-Server", "transport/server/play-akka-http-server")
    .dependsOn(PlayServerProject, StreamsProject)
    .dependsOn(PlayGuiceProject % "test")
    .settings(
      publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
      libraryDependencies ++= specs2Deps.map(_ % "test"),
      libraryDependencies += akkaHttp
    )

lazy val PlayAkkaHttp2SupportProject =
  PlayCrossBuiltProject("Play-Akka-Http2-Support", "transport/server/play-akka-http2-support")
    .dependsOn(PlayAkkaHttpServerProject)
    .settings(
      publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
      libraryDependencies += akkaHttp2Support
    )

lazy val PlayClusterSharding = PlayCrossBuiltProject("Play-Cluster-Sharding", "cluster/play-cluster-sharding")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= clusterDependencies
  )
  .dependsOn(PlayProject)

lazy val PlayJavaClusterSharding =
  PlayCrossBuiltProject("Play-Java-Cluster-Sharding", "cluster/play-java-cluster-sharding")
    .settings(
      publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
      libraryDependencies ++= clusterDependencies
    )
    .dependsOn(PlayProject)

lazy val PlayJdbcApiProject = PlayCrossBuiltProject("Play-JDBC-Api", "persistence/play-jdbc-api")
  .dependsOn(PlayProject)
  .settings(publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))))

lazy val PlayJdbcProject: Project = PlayCrossBuiltProject("Play-JDBC", "persistence/play-jdbc")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= jdbcDeps
  )
  .dependsOn(PlayJdbcApiProject)
  .dependsOn(PlaySpecs2Project % "test")

lazy val PlayJdbcEvolutionsProject = PlayCrossBuiltProject("Play-JDBC-Evolutions", "persistence/play-jdbc-evolutions")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies += derbyDatabase % Test
  )
  .dependsOn(PlayJdbcApiProject)
  .dependsOn(PlaySpecs2Project % "test")
  .dependsOn(PlayJdbcProject % "test->test")
  .dependsOn(PlayJavaJdbcProject % "test")

lazy val PlayJavaJdbcProject = PlayCrossBuiltProject("Play-Java-JDBC", "persistence/play-java-jdbc")
  .settings(publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))))
  .dependsOn(PlayJdbcProject % "compile->compile;test->test", PlayJavaProject)
  .dependsOn(PlaySpecs2Project % "test", PlayGuiceProject % "test")

lazy val PlayJpaProject = PlayCrossBuiltProject("Play-Java-JPA", "persistence/play-java-jpa")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= jpaDeps
  )
  .dependsOn(PlayJavaJdbcProject % "compile->compile;test->test")
  .dependsOn(PlayJdbcEvolutionsProject % "test")
  .dependsOn(PlaySpecs2Project % "test")

lazy val PlayTestProject = PlayCrossBuiltProject("Play-Test", "testkit/play-test")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= testDependencies ++ Seq(h2database % "test"),
    (Test / parallelExecution) := false
  )
  .dependsOn(
    PlayGuiceProject,
    PlayServerProject,
    // We still need a server provider when running Play-Test tests.
    // Since Akka HTTP is the default, we should use it here.
    PlayAkkaHttpServerProject % "test"
  )

lazy val PlaySpecs2Project = PlayCrossBuiltProject("Play-Specs2", "testkit/play-specs2")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= specs2Deps,
    (Test / parallelExecution) := false
  )
  .dependsOn(PlayTestProject)

lazy val PlayJavaProject = PlayCrossBuiltProject("Play-Java", "core/play-java")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= javaDeps ++ javaTestDeps
  )
  .dependsOn(
    PlayProject       % "compile;test->test",
    PlayTestProject   % "test",
    PlaySpecs2Project % "test",
    PlayGuiceProject  % "test"
  )

lazy val PlayJavaFormsProject = PlayCrossBuiltProject("Play-Java-Forms", "web/play-java-forms")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= javaDeps ++ javaFormsDeps ++ javaTestDeps
  )
  .dependsOn(
    PlayJavaProject % "compile;test->test"
  )

lazy val PlayDocsProject = PlayCrossBuiltProject("Play-Docs", "dev-mode/play-docs")
  .settings(Docs.settings: _*)
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= playDocsDependencies
  )
  .dependsOn(PlayAkkaHttpServerProject)

lazy val PlayGuiceProject = PlayCrossBuiltProject("Play-Guice", "core/play-guice")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= guiceDeps ++ specs2Deps.map(_ % "test")
  )
  .dependsOn(
    PlayProject % "compile;test->test"
  )

lazy val SbtPluginProject = PlaySbtPluginProject("Sbt-Plugin", "dev-mode/sbt-plugin")
  .enablePlugins(SbtPlugin)
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= sbtDependencies((pluginCrossBuild / sbtVersion).value, scalaVersion.value),
    dependencyOverrides ++= Seq(
      "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.0"
    ),
    (Compile / sourceGenerators) += Def.task {
      PlayVersion(
        version.value,
        "2.13.7",
        sbtVersion.value,
        Dependencies.akkaVersion,
        Dependencies.akkaHttpVersion,
        (Compile / sourceManaged).value
      )
    }.taskValue,
    (Compile / headerSources) ++= (sbtTestDirectory.value ** ("*.scala" || "*.java")).get,
  )
  .dependsOn(SbtRoutesCompilerProject, RunSupportProject)

lazy val SbtScriptedToolsProject = PlaySbtPluginProject("Sbt-Scripted-Tools", "dev-mode/sbt-scripted-tools")
  .enablePlugins(SbtPlugin)
  .dependsOn(SbtPluginProject)
  .settings(publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))))

lazy val PlayLogback = PlayCrossBuiltProject("Play-Logback", "core/play-logback")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies += logback,
    (Test / parallelExecution) := false,
    // quieten deprecation warnings in tests
    (Test / scalacOptions) := (Test / scalacOptions).value.diff(Seq("-deprecation"))
  )
  .dependsOn(PlayProject)
  .dependsOn(PlaySpecs2Project % "test")

lazy val PlayWsProject = PlayCrossBuiltProject("Play-WS", "transport/client/play-ws")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= playWsDeps,
    (Test / parallelExecution) := false,
    // quieten deprecation warnings in tests
    (Test / scalacOptions) := (Test / scalacOptions).value.diff(Seq("-deprecation"))
  )
  .dependsOn(PlayProject)
  .dependsOn(PlayTestProject % "test")

lazy val PlayAhcWsProject = PlayCrossBuiltProject("Play-AHC-WS", "transport/client/play-ahc-ws")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= playAhcWsDeps,
    dependencyOverrides ++= Seq(
      "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.0"
    ),
    (Test / parallelExecution) := false,
    // quieten deprecation warnings in tests
    (Test / scalacOptions) := (Test / scalacOptions).value.diff(Seq("-deprecation"))
  )

  .dependsOn(PlayWsProject, PlayCaffeineCacheProject % "test")
  .dependsOn(PlaySpecs2Project % "test")
  .dependsOn(PlayTestProject % "test->test")
  .dependsOn(PlayAkkaHttpServerProject % "test") // Because we need a server provider when running the tests

lazy val PlayOpenIdProject = PlayCrossBuiltProject("Play-OpenID", "web/play-openid")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    dependencyOverrides ++= Seq(
      "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.0"
    ),
    (Test / parallelExecution) := false,
    // quieten deprecation warnings in tests
    (Test / scalacOptions) := (Test / scalacOptions).value.diff(Seq("-deprecation"))
  )
  .dependsOn(PlayAhcWsProject)
  .dependsOn(PlaySpecs2Project % "test")

lazy val PlayFiltersHelpersProject = PlayCrossBuiltProject("Filters-Helpers", "web/play-filters-helpers")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= playFilterDeps,
    (Test / parallelExecution) := false
  )
  .dependsOn(
    PlayProject,
    PlayTestProject           % "test",
    PlayJavaProject           % "test",
    PlaySpecs2Project         % "test",
    PlayAhcWsProject          % "test",
    PlayAkkaHttpServerProject % "test" // Because we need a server provider when running the tests
  )

lazy val PlayIntegrationTestProject = PlayCrossBuiltProject("Play-Integration-Test", "core/play-integration-test")
// This project is just for testing Play, not really a public artifact
  .settings(disablePublishing)
  .configs(IntegrationTest)
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    Defaults.itSettings,
    inConfig(IntegrationTest)(ScalafmtPlugin.scalafmtConfigSettings),
    headerSettings(IntegrationTest),
    inConfig(IntegrationTest)(JavaFormatterPlugin.toBeScopedSettings),
    libraryDependencies += okHttp % IntegrationTest,
    (IntegrationTest / parallelExecution) := false,
    mimaPreviousArtifacts := Set.empty,
    (IntegrationTest / fork) := true,
    (IntegrationTest / javaOptions) += "-Dfile.encoding=UTF8",
  )
  .dependsOn(
    PlayProject       % "it->test",
    PlayLogback       % "it->test",
    PlayAhcWsProject  % "it->test",
    PlayServerProject % "it->test",
    PlaySpecs2Project
  )
  .dependsOn(PlayFiltersHelpersProject)
  .dependsOn(PlayJavaProject)
  .dependsOn(PlayJavaFormsProject)
  .dependsOn(PlayAkkaHttpServerProject)
  .dependsOn(PlayAkkaHttp2SupportProject)
  .dependsOn(PlayNettyServerProject)

// NOTE: this project depends on JMH, which is GPLv2.
lazy val PlayMicrobenchmarkProject = PlayCrossBuiltProject("Play-Microbenchmark", "core/play-microbenchmark")
  .enablePlugins(JmhPlugin)
  // This project is just for microbenchmarking Play. Not published.
  .settings(disablePublishing)
  .settings(
    dependencyOverrides ++= Seq(
      "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.0"
    ),
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    // Change settings so that IntelliJ can handle dependencies
    // from JMH to the integration tests. We can't use "compile->test"
    // when we depend on the integration test project, we have to use
    // "test->test" so that IntelliJ can handle it. This means that
    // we need to put our JMH sources into src/test so they can pick
    // up the integration test files.
    // See: https://github.com/ktoso/sbt-jmh/pull/73#issue-163891528
    (Jmh / classDirectory) := (Test / classDirectory).value,
    (Jmh / dependencyClasspath) := (Test / dependencyClasspath).value,
    (Jmh / generateJmhSourcesAndResources) := (Jmh / generateJmhSourcesAndResources).dependsOn((Test / compile)).value,
    (Jmh / run / mainClass) := Some("org.openjdk.jmh.Main"),
    (Test / parallelExecution) := false,
    mimaPreviousArtifacts := Set.empty
  )
  .dependsOn(
    PlayProject                % "test->test",
    PlayLogback                % "test->test",
    PlayIntegrationTestProject % "test->it",
    PlayAhcWsProject,
    PlaySpecs2Project,
    PlayFiltersHelpersProject,
    PlayJavaProject,
    PlayNettyServerProject,
    PlayAkkaHttpServerProject,
    PlayAkkaHttp2SupportProject
  )

lazy val PlayCacheProject = PlayCrossBuiltProject("Play-Cache", "cache/play-cache")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= playCacheDeps
  )
  .dependsOn(
    PlayProject,
    PlaySpecs2Project % "test"
  )

lazy val PlayEhcacheProject = PlayCrossBuiltProject("Play-Ehcache", "cache/play-ehcache")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= playEhcacheDeps
  )
  .dependsOn(
    PlayProject,
    PlayCacheProject,
    PlaySpecs2Project % "test"
  )

lazy val PlayCaffeineCacheProject = PlayCrossBuiltProject("Play-Caffeine-Cache", "cache/play-caffeine-cache")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= playCaffeineDeps
  )
  .dependsOn(
    PlayProject,
    PlayCacheProject,
    PlaySpecs2Project % "test"
  )

// JSR 107 cache bindings (note this does not depend on ehcache)
lazy val PlayJCacheProject = PlayCrossBuiltProject("Play-JCache", "cache/play-jcache")
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= jcacheApi
  )
  .dependsOn(
    PlayProject,
    PlayCaffeineCacheProject % "test", // provide a cachemanager implementation
    PlaySpecs2Project        % "test"
  )

lazy val PlayDocsSbtPlugin = PlaySbtPluginProject("Play-Docs-Sbt-Plugin", "dev-mode/play-docs-sbt-plugin")
  .enablePlugins(SbtPlugin)
  .enablePlugins(SbtTwirl)
  .settings(
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    libraryDependencies ++= playDocsSbtPluginDependencies,
    scriptedDependencies := (()), // drop Test/compile & publishLocal being called on aggregating root scripted
  )
  .dependsOn(SbtPluginProject)

// These projects are aggregate by the root project and every
// task (compile, test, publish, etc) executed for the root
// project will also be executed for them:
// https://www.scala-sbt.org/1.x/docs/Multi-Project.html#Aggregation
//
// Keep in mind that specific configurations (like skip in publish) will be respected.
lazy val userProjects = Seq[ProjectReference](
  PlayProject,
  PlayGuiceProject,
  BuildLinkProject,
  RoutesCompilerProject,
  PlayAkkaHttpServerProject,
  PlayAkkaHttp2SupportProject,
  PlayCacheProject,
  PlayEhcacheProject,
  PlayCaffeineCacheProject,
  PlayJCacheProject,
  PlayJdbcApiProject,
  PlayJdbcProject,
  PlayJdbcEvolutionsProject,
  PlayJavaProject,
  PlayJavaFormsProject,
  PlayJodaFormsProject,
  PlayJavaJdbcProject,
  PlayJpaProject,
  PlayNettyServerProject,
  PlayServerProject,
  PlayLogback,
  PlayWsProject,
  PlayAhcWsProject,
  PlayOpenIdProject,
  PlaySpecs2Project,
  PlayTestProject,
  PlayExceptionsProject,
  PlayFiltersHelpersProject,
  StreamsProject,
  PlayClusterSharding,
  PlayJavaClusterSharding
)
lazy val nonUserProjects = Seq[ProjectReference](
  PlayMicrobenchmarkProject,
  PlayDocsProject,
  PlayIntegrationTestProject,
  PlayDocsSbtPlugin,
  RunSupportProject,
  SbtRoutesCompilerProject,
  SbtPluginProject,
  SbtScriptedToolsProject,
  billOfMaterials
)

lazy val PlayFramework = Project("Play-Framework", file("."))
  .enablePlugins(PlayRootProject)
  .settings(
    playCommonSettings,
    scalaVersion := (PlayProject / scalaVersion).value,
    version := "2.9.0-SNAPSHOT",
    dependencyOverrides ++= Seq(
      "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.0"
    ),
    crossScalaVersions := Nil,
    (ThisBuild / playBuildRepoName) := "playframework",
    (Global / concurrentRestrictions) += Tags.limit(Tags.Test, 1),
    libraryDependencies ++= (runtime(scalaVersion.value) ++ jdbcDeps),
    Docs.apiDocsInclude := false,
    Docs.apiDocsIncludeManaged := false,
    mimaReportBinaryIssues := (()),
    commands += Commands.quickPublish,
    publishTo := Some(Resolver.file("file", new File("~/Development/sting-mvn-repo"))),
    Release.settings,
    publish / skip := true,
  )
  .aggregate((userProjects ++ nonUserProjects): _*)

ThisBuild / version ~= (x => "2.9.0-SNAPSHOT")
ThisBuild / dynver  ~= (x => "2.9.0-SNAPSHOT")

addCommandAlias(
  "validateCode",
  List(
    "headerCheckAll",
    "scalafmtSbtCheck",
    "scalafmtCheckAll",
    "javafmtCheckAll",
    "+checkAkkaModuleVersions"
  ).mkString(";")
)
