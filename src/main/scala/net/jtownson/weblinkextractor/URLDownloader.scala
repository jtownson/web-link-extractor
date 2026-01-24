package net.jtownson.weblinkextractor

import org.apache.commons.io.IOUtils

import java.net.URL
import scala.io.Source
import scala.util.Using

// Extracts the markup from each URL...
trait URLDownloader:
  def urlMarkup(url: URL): String

object URLDownloader:
  // in 'real life' many edge cases:
  // read the actual encoding header for HTTP(S), BOMs for FTP, handle errors, timeouts, redirects, authentication, redirects, etc.
  def scalaSourceDownloader: URLDownloader =
    (url: URL) => Using.resource(Source.fromURL(url, "UTF-8"))(_.mkString)

  def commonsIODownloader: URLDownloader =
    (url: URL) => IOUtils.toString(url, "UTF-8")