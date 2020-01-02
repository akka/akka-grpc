/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
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
