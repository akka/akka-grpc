import java.io.{BufferedInputStream, FileInputStream}
import java.util.Properties

version in ThisBuild := {

  val p = new Properties()
  val bos = new BufferedInputStream(new FileInputStream("version.properties"))
  try {
    p.load(bos)
    p.getProperty("project.version")
  } finally bos.close()
}
