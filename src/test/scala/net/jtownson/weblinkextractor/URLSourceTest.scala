package net.jtownson.weblinkextractor

import net.jtownson.weblinkextractor.URLSource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.*

import java.net.URI
import scala.io.Source

class URLSourceTest extends AnyFlatSpec {

  behavior of "URLSource."

  it should "parse URLs from some Source, ignoring invalid ones" in {
    val givn = Source.fromString(
      """http://example.com
        |https://openai.com
        |invalid-url
        |
        |ftp://ftp.example.com
        |file:///Users/foo/conf.json
        |""".stripMargin)

    val expected = List(
      "http://example.com",
      "https://openai.com",
      "ftp://ftp.example.com",
      "file:///Users/foo/conf.json"
    ).map(URI.create(_).toURL)

    val uris = URLSource.fromSource(givn).urls

    uris shouldBe expected
  }
}
