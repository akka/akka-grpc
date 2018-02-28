package akka.grpc.gen

import java.io.{ BufferedOutputStream, ByteArrayOutputStream }

import scala.annotation.tailrec
import akka.http.grpc._

object Main extends App {

  val inBytes: Array[Byte] = {
    val baos = new ByteArrayOutputStream(math.max(64, System.in.available()))
    val buffer = new Array[Byte](32 * 1024)

    var bytesRead = System.in.read(buffer)
    while (bytesRead >= 0) {
      baos.write(buffer, 0, bytesRead)
      bytesRead = System.in.read(buffer)
    }
    baos.toByteArray
  }

  //  import java.io.FileOutputStream
  //  private val req = new FileOutputStream("/tmp/req.p")
  //  req.write(inBytes)
  //  req.close()

  // For now we hard-code the Java Server code generator here,
  // but options can be passed in via `request.getParameter` to
  // make this more flexible in the future
  val outBytes = new JavaServerCodeGenerator().run(inBytes)

  //  import java.io.FileOutputStream
  //  private val res = new FileOutputStream("/tmp/res.p")
  //  res.write(outBytes)
  //  res.close()

  val bos = new BufferedOutputStream(System.out)
  bos.write(outBytes)
  bos.flush()
}
