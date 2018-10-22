lazy val commonSettings = Seq(
    organization := "io.github.pityka",
    scalaVersion := "2.12.6",
    crossScalaVersions := Seq("2.11.11"),
    version := "1.2.3"
  )

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "selfpackage",
    libraryDependencies ++= Seq(
      "io.github.lukehutch" % "fast-classpath-scanner" % "2.0.19"),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    publishTo := sonatypePublishTo.value,
    pomExtra in Global := {
      <url>https://pityka.github.io/selfpackage/</url>
     <scm>
       <connection>scm:git:github.com/pityka/selfpackage</connection>
       <developerConnection>scm:git:git@github.com:pityka/selfpackage</developerConnection>
       <url>github.com/pityka/selfpackage</url>
     </scm>
     <developers>
       <developer>
         <id>pityka</id>
         <name>Istvan Bartha</name>
         <url>https://pityka.github.io/selfpackage/</url>
       </developer>
     </developers>
    }
  )

lazy val testProject = (project in file("test"))
  .settings(commonSettings: _*)
  .settings(
    name := "selfpackage-test",
    publishArtifact := false
  )
  .dependsOn(root)
  .enablePlugins(JavaAppPackaging)
