package selfpackage

import com.google.cloud.tools.jib.api.Containerizer
import com.google.cloud.tools.jib.api.ImageReference
import io.github.classgraph.ClassGraph
import java.io.File
import scala.collection.JavaConverters._
import com.google.cloud.tools.jib.api.Jib
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath
import com.google.cloud.tools.jib.api.JibContainer

object jib {

  def containerize(
      out: Containerizer,
      mainClassNameArg: Option[String] = None,
      base: Option[ImageReference] = None
  ): JibContainer = {
    val mainClassName = mainClassNameArg.getOrElse {
      mainClass("main").getOrElse(
        throw new RuntimeException(
          "Thread with name main not found. Can't infer main class name."
        )
      )
    }

    val classpathFilesOrFolders =
      (new ClassGraph()).getClasspathFiles.asScala

    val tmp = File.createTempFile("self", "")

    val files = classpathFilesOrFolders.filter(_.isFile)
    val folders = classpathFilesOrFolders.filter(_.isDirectory)

    val jarFromFolders = folders.zipWithIndex.map { case (folder, idx) =>
      writeJar(
        folder,
        new File(tmp.getAbsolutePath + "." + idx + ".jar"),
        folder.getAbsolutePath()
      )
    }

    val (snapshots, libs) =
      (jarFromFolders ++ files).partition(_.getName.contains("SNAPSHOT"))

    val scriptFunctions = """

    declare -a residual_args
    declare -a java_args
    addJava () {
      java_args+=( "$1" )
    }
    addResidual () {
      residual_args+=( "$1" )
    }
    process_args () {
      local no_more_snp_opts=0
      while [[ $# -gt 0 ]]; do
        case "$1" in
                 --) shift && no_more_snp_opts=1 && break ;;
                 -D*) addJava "$1" && shift ;;
                -J*) addJava "${1:2}" && shift ;;
                  *) addResidual "$1" && shift ;;
        esac
      done

      if [[ $no_more_snp_opts ]]; then
        while [[ $# -gt 0 ]]; do
          addResidual "$1" && shift
        done
      fi
    }
    if [[ "$JAVA_OPTS" != "" ]]; then
      java_opts="${JAVA_OPTS}"
    fi

    process_args "$@"
    set -- "${residual_args[@]}"

    """

    val script =
      s"""|#!/usr/bin/env bash
    |$scriptFunctions
    |script_root=$$(dirname $$0)
    |classpath=${(libs.map(l => "${script_root}/lib/" + l.getName) ++ snapshots
           .map(l => "${script_root}/snapshots/" + l.getName)).mkString(":")}
    |exec java $${java_opts[@]} "$${java_args[@]}" -cp $$classpath $mainClassName "$${residual_args[@]}"
    """.stripMargin
    val tmpFolder = java.io.File.createTempFile("entrypoint", "folder")
    tmpFolder.delete()
    assert(tmpFolder.mkdir())
    val scriptTmp = new File(tmpFolder,"entrypoint.sh")

    java.nio.file.Files.writeString(scriptTmp.toPath(), script)

    val jibContainer = Jib
      .from(
        base.getOrElse(
          ImageReference.of(
            null,
            "library/eclipse-temurin",
            "17.0.13_11-jre-ubi9-minimal",
            "sha256:4142ab239eea1ea327816283279c9f126cf87128ce2411208f767c0a63084aa3"
          )
        )
      )
      .addLayer(libs.map(_.toPath).asJava, AbsoluteUnixPath.get("/app/lib/"))
      .addLayer(
        snapshots.map(_.toPath).asJava,
        AbsoluteUnixPath.get("/app/snapshots/")
      )
      .addLayer(List(scriptTmp.toPath).asJava, AbsoluteUnixPath.get("/app/"))
      .setEntrypoint("bash", "/app/entrypoint.sh")
      .containerize(out);

      scriptTmp.delete()
      jarFromFolders.foreach(_.delete)

      jibContainer
  }

}
