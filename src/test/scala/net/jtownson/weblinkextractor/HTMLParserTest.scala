package net.jtownson.weblinkextractor

import net.jtownson.weblinkextractor.HTMLParser
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.*

class HTMLParserTest extends AnyFlatSpec {

  behavior of "HTMLParser"

  it should "extract links from HTML markup" in {
    val html =
      """<html>
        | <body>
        |   <a href="https://example.com/1">Link 1</a>
        |   <a href='email:john.doe@gmail.com'>Link 2</a>
        |   <a href=ftp://ftp.example.com/3>Link 3</a>
        |   <a>No href attribute</a>
        |   <p>Some other content</p>
        | </body>
        |</html>""".stripMargin

    val expectedLinks = List(
      "https://example.com/1",
      "email:john.doe@gmail.com",
      "ftp://ftp.example.com/3"
    )

    val extractedLinks = HTMLParser.jsoupHTMLParser.extractLinks(html)

    extractedLinks shouldBe expectedLinks
  }

}
