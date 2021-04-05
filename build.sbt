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
  organization := "io.github.pityka",
  scalaVersion := "2.13.5",
  crossScalaVersions := Seq("2.12.13", "2.13.5"),
  libraryDependencies ++= (scalaVersion.value match {
    case "2.12.13" => Nil
    case "2.13.5" =>
      List("org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.2")
  })
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "selfpackage",
    libraryDependencies ++= Seq(
      "io.github.lukehutch" % "fast-classpath-scanner" % "2.0.21"
    )
  )

lazy val testProject = (project in file("test"))
  .settings(commonSettings: _*)
  .settings(
    name := "selfpackage-test",
    publishArtifact := false,
    skip in publish := true
  )
  .dependsOn(root)
  .enablePlugins(JavaAppPackaging)
