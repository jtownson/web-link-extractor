package net.jtownson.weblinkextractor.testsupport

import java.net.URL

sealed trait Event

object Event {
  case class StartedDownload(url: URL) extends Event

  case class Downloaded(url: URL) extends Event

  case class Consumed(markup: String) extends Event

  case class DownloadFailed(url: URL) extends Event

  case class ConsumeFailed(markup: String) extends Event
}
