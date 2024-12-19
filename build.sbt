inThisBuild(
  List(
    organization := "io.github.pityka",
    homepage := Some(url("https://pityka.github.io/selfpackage/")),
    licenses := List(("MIT", url("https://opensource.org/licenses/MIT"))),
    developers := List(
      Developer(
        "pityka",
        "Istvan Bartha",
        "bartha.pityu@gmail.com",
        url("https://github.com/pityka/selfpackage")
      )
    )
  )
)

lazy val commonSettings = Seq(
  scalaVersion := "2.13.13",
  crossScalaVersions := Seq("2.13.13", "3.3.3"),
  mimaPreviousArtifacts := (scalaVersion.value match {
    case "2.13.13" =>
      Set(
        organization.value %% moduleName.value % "1.2.5"
      )
    case "3.3.3" => Set.empty
  }),
  libraryDependencies ++= List(
    "org.scala-lang.modules" %% "scala-parallel-collections" % "1.1.0"
  )
)

lazy val root = project
  .in(file("."))
  .settings(commonSettings: _*)
  .settings(
    publish / skip := true,
    publishArtifact := false,
    mimaPreviousArtifacts := Set.empty,
    name := "selfpackage-root"
  )
  .aggregate(core, jib)

lazy val core = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "selfpackage",
    libraryDependencies ++= Seq(
      "io.github.classgraph" % "classgraph" % "4.8.160"
    )
  )

lazy val jib = (project in file("jib"))
  .settings(commonSettings: _*)
  .settings(
    name := "selfpackage-jib",
    mimaPreviousArtifacts := Set.empty,
    libraryDependencies ++= Seq(
      "com.google.cloud.tools" % "jib-core" % "0.27.2"
    )
  )
  .dependsOn(core)

lazy val testProject = (project in file("test"))
  .settings(commonSettings: _*)
  .settings(
    name := "selfpackage-test",
    publishArtifact := false,
    skip in publish := true
  )
  .dependsOn(core)
  .dependsOn(jib)
  .enablePlugins(JavaAppPackaging)
