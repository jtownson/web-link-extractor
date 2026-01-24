package net.jtownson.weblinkextractor

import net.jtownson.weblinkextractor.URLDownloader
import net.jtownson.weblinkextractor.URLDownloader.{commonsIODownloader, scalaSourceDownloader}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.*

import scala.io.Source
import scala.util.Using

class URLDownloaderTest extends AnyFlatSpec {

  behavior of "URLDownloader"

  it should "download markup from a URL using commons-io" in {
    testDownloadWith(commonsIODownloader)
  }

  it should "download markup from a URL using scala.io.Source" in {
    testDownloadWith(scalaSourceDownloader)
  }
  
  private def testDownloadWith(urlDownloader: URLDownloader) = {
    val resourceName = "example.com.html"
    val url = getClass.getClassLoader.getResource(resourceName)
    val expected = Using.resource(Source.fromResource(resourceName))(_.mkString)

    val page = scalaSourceDownloader.urlMarkup(url)

    page shouldBe expected
  }
}
