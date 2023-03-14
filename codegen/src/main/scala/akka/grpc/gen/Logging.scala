/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

import java.io.PrintWriter

// specific to gen so that the build tools can implement their own
trait Logger {
  def debug(text: String): Unit
  def info(text: String): Unit
  def warn(text: String): Unit
  def error(text: String): Unit
}

/**
 * Simple standard out logger for use in tests or where there is no logger from the build tool available
 */
object StdoutLogger extends Logger {
  def debug(text: String): Unit = println(s"[debug] $text")
  def info(text: String): Unit = println(s"[info] $text")
  def warn(text: String): Unit = println(s"[warn] $text")
  def error(text: String): Unit = println(s"[error] $text")
}

object SilencedLogger extends Logger {
  def debug(text: String): Unit = ()
  def info(text: String): Unit = ()
  def warn(text: String): Unit = ()
  def error(text: String): Unit = ()
}

class FileLogger(path: String) extends Logger {
  val printer = new PrintWriter(path, "UTF-8")
  def debug(text: String): Unit = {
    printer.println(s"[debug] $text")
    printer.flush()
  }
  def info(text: String): Unit = {
    printer.println(s"[info] $text")
    printer.flush()
  }
  def warn(text: String): Unit = {
    printer.println(s"[warn] $text")
    printer.flush()
  }
  def error(text: String): Unit = {
    printer.println(s"[error] $text")
    printer.flush()
  }
}

/**
 * Logger that forwards calls to another Logger via reflection.
 *
 *  This enables a code generator that is loaded inside a sandboxed class loader to
 *  use a logger that lives in a different class loader.
 */
class ReflectiveLogger(logger: Object) extends Logger {
  private val debugMethod = logger.getClass.getMethod("debug", classOf[String])
  private val infoMethod = logger.getClass.getMethod("info", classOf[String])
  private val warnMethod = logger.getClass.getMethod("warn", classOf[String])
  private val errorMethod = logger.getClass.getMethod("error", classOf[String])

  def debug(text: String): Unit = debugMethod.invoke(logger, text)
  def info(text: String): Unit = infoMethod.invoke(logger, text)
  def warn(text: String): Unit = warnMethod.invoke(logger, text)
  def error(text: String): Unit = errorMethod.invoke(logger, text)
}
