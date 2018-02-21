import java.io.{BufferedInputStream, FileInputStream}
import java.util.Properties

val theVersion = {
  val p = new Properties()
  val bos = new BufferedInputStream(new FileInputStream("../version.properties"))
  try {
    p.load(bos)
    p.getProperty("project.version")
  } finally bos.close()
}

addSbtPlugin("com.lightbend.akka.grpc" % "akka-grpc-sbt-plugin" % theVersion)

addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")