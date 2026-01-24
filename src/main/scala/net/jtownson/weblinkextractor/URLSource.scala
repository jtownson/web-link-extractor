package net.jtownson.weblinkextractor

import java.net.{URI, URL}
import scala.io.Source
import scala.util.Using

// Receives a list of URLs (from file, command line, etc.).
trait URLSource:
  def urls: List[URL]

object URLSource:

  def fromList(urlList: List[URL]): URLSource = new URLSource {
    def urls: List[URL] = urlList
  }

  def fromResource(resourceName: String): URLSource =
    fromSource(Source.fromResource(resourceName))

  def fromSource(source: Source): URLSource = new URLSource {
    def urls: List[URL] =
      Using.resource(source) { resource =>
        resource.getLines().toList.flatMap { line =>
          try
            Some(URI.create(line.trim).toURL)
          catch case _: Exception =>
            None
        }
      }
  }