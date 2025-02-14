package mypackage
import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean
import scala.collection.JavaConverters._
import scala.sys.process._
import scala.language.postfixOps
import com.google.cloud.tools.jib.api.TarImage
import com.google.cloud.tools.jib.api.LogEvent

object MyApp extends App {
  println("App args: " + args.mkString("\"", "\",\"", "\""))
  println(
    "JVM args: " + ManagementFactory.getRuntimeMXBean.getInputArguments.asScala
      .mkString("\"", "\",\"", "\"")
  )

  println("Packaging..")
  selfpackage.write(new java.io.File("me"))
  selfpackage.jib.containerize(
    com.google.cloud.tools.jib.api.Containerizer
      .to(TarImage.at(new java.io.File("me.tar").toPath).named("test"))
      .addEventHandler(
        classOf[LogEvent],
        (logEvent:LogEvent) => System.out
          .println(logEvent.getLevel() + ": " + logEvent.getMessage())
      ),
      pathInContainer = "/opt"
  )
  "chmod u+x me" !
}
