package net.jtownson.weblinkextractor

import org.jsoup.Jsoup

import scala.jdk.CollectionConverters.*

// Parses the HTML and extracts all hyperlinks into a list.
trait HTMLParser:
  def extractLinks(html: String): List[String]

object HTMLParser:
  def jsoupHTMLParser: HTMLParser = (html: String) =>
    val doc = Jsoup.parse(html)
    val links = doc.select("a[href]")
    links.eachAttr("href").asScala.toList