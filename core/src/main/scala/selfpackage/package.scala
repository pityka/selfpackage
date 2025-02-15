import io.github.classgraph.ClassGraph
import java.io._
import java.util.jar._
import java.util.zip._
import scala.collection.JavaConverters._
import scala.util._
import java.nio.file._
import java.nio.file.attribute._
import scala.language.postfixOps

package object selfpackage {

  private[selfpackage] object CompatParColls {
    val Converters = {
      import Compat._

      {
        import scala.collection.parallel._

        CollectionConverters
      }
    }

    object Compat {
      object CollectionConverters
    }
  }

  import CompatParColls.Converters._

  def mainClass(threadName: String): Option[String] =
    Thread.getAllStackTraces.asScala.find(_._1.getName == threadName).map {
      case (threadName, stackTrace) =>
        stackTrace.last.getClassName
    }

  def commonprefix(s1: String, s2: String) =
    s1 zip s2 takeWhile (x => x._1 == x._2)

  def copy(is: InputStream, os: OutputStream, bufferSize: Int): Unit = {
    val buffer = Array.ofDim[Byte](bufferSize)
    var count = is.read(buffer)
    while (count != -1) {
      os.write(buffer, 0, count)
      count = is.read(buffer)
    }
  }

  def writeJar(input: File, out: File, removePrefix: String): File = {
    val tmp = File.createTempFile("self", "")

    val fos = new BufferedOutputStream(new FileOutputStream(tmp))
    val jos = new JarOutputStream(fos);
    val fileVisitor = new SimpleFileVisitor[Path] {
      override def visitFile(path: Path, attrs: BasicFileAttributes) = {
        val file = path.toFile
        val br = new BufferedInputStream(new FileInputStream(file))
        // Find the relative path on the class path
        val fileNameInJar = {
          val fa = file.getAbsolutePath
          fa.drop(removePrefix.size).dropWhile(_ == '/')
        }
        jos.putNextEntry(new JarEntry(fileNameInJar));
        copy(br, jos, 8192)
        br.close
        jos.closeEntry
        FileVisitResult.CONTINUE
      }
    }
    Files.walkFileTree(input.toPath, fileVisitor)
    jos.close
    fos.close
    Files.copy(tmp.toPath, out.toPath)
    tmp.delete
    out
  }

  def write(out: File, mainClassNameArg: Option[String] = None): Unit = {
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

    val jarFromFolders = folders.zipWithIndex.par.map { case (folder, idx) =>
      writeJar(
        folder,
        new File(tmp.getAbsolutePath + "." + idx + ".jar"),
        folder.getAbsolutePath()
      )
    } seq

    // if this is modified then the 249 count must be modified as well
    val selfExtraction = """|#!/usr/bin/env bash
    |set -e 
    |mkdir -p $0-extract 
    |tail -c +190 $0 | tar -x -C $0-extract 2> /dev/null 1> /dev/null || true
    |chmod u+x $0-extract/entrypoint 
    |exec $0-extract/entrypoint $@
    """.stripMargin

    val fos = new BufferedOutputStream(new FileOutputStream(tmp))
    fos.write(selfExtraction.getBytes("UTF-8"))
    val zos = new org.kamranzafar.jtar.TarOutputStream(fos);
    val libs = (jarFromFolders ++ files).map { file =>
      val br = new BufferedInputStream(new FileInputStream(file))
      val te = new org.kamranzafar.jtar.TarEntry(file, "lib/" + file.getName)
      zos.putNextEntry(te);
      copy(br, zos, 8192)
      br.close
      zos.flush()
      "lib/" + file.getName
    }
    zos.flush()

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

    val script = s"""|#!/usr/bin/env bash
    |$scriptFunctions
    |script_root=$$(dirname $$0)
    |classpath=${libs.map(l => "${script_root}/" + l).mkString(":")}
    |exec java $${java_opts[@]} "$${java_args[@]}" -cp $$classpath $mainClassName "$${residual_args[@]}"
    """.stripMargin

    val ba = script.getBytes("UTF-8")
    val th = org.kamranzafar.jtar.TarHeader.createHeader(
      "entrypoint",
      ba.length,
      1,
      false,
      0x777
    );
    val te = new org.kamranzafar.jtar.TarEntry(th)
    zos.putNextEntry(te)
    zos.write(ba)
    zos.flush()
    zos.close()
    fos.close
    Files.copy(tmp.toPath, out.toPath, StandardCopyOption.REPLACE_EXISTING)

  }
}
