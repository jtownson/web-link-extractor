package net.jtownson.weblinkextractor

import cats.effect.std.Queue
import cats.effect.{ExitCode, IO, IOApp}
import Fs2ProducerConsumer.{consumer, producer}

object WebLinkExtractorApp extends IOApp.Simple {

  private val urlDownloader = URLDownloader.scalaSourceDownloader
  private val htmlParser = HTMLParser.jsoupHTMLParser
  private val queueMaxSize = 100
  private val downloadConcurrency = 4
  private val urlSource = URLSource.fromResource("url-list.txt")

  private val showLinks: List[String] => IO[Unit] = links =>
    IO.println(s"\n--- site links ---\n${links.mkString("\n")}")

  override def run: IO[Unit] =
    for {
      queue <- Queue.circularBuffer[IO, Option[String]](queueMaxSize)

      produce = producer(urlSource, urlDownloader, downloadConcurrency, queue)

      consume = consumer(queue, htmlParser, showLinks)

      _ <- produce.concurrently(consume).compile.drain
    } yield ExitCode.Success
}
