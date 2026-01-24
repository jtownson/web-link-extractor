package net.jtownson.weblinkextractor

import cats.effect.*
import cats.effect.std.{Console, Queue}
import cats.implicits.catsSyntaxApplicativeError
import cats.syntax.all.*
import fs2.Stream
import net.jtownson.weblinkextractor.{HTMLParser, URLDownloader, URLSource}

import java.net.URL
object Fs2ProducerConsumer {

  private def simpleErrorHandler[T, F[_]: {Async, Console}](msg: String, t: Throwable): F[Either[Throwable, T]] = {
    Console[F].println(msg) *>
      Async[F].pure(Left(t))
  }

  private def download[F[_] : {Async, Console}](urlDownloader: URLDownloader)(maybeUrl: Option[URL]): F[Either[Throwable, Option[String]]] =
    Async[F]
      .blocking(maybeUrl.map(urlDownloader.urlMarkup))
      .map(Right(_))
      .handleErrorWith(t => simpleErrorHandler(s"download failed for url $maybeUrl", t))

  private def parseLinks[F[_] : {Async, Console}](htmlParser: HTMLParser)(markup: String): F[Either[Throwable, List[String]]] =
    Sync[F]
      .blocking(htmlParser.extractLinks(markup))
      .map(Right(_))
      .handleErrorWith(t => simpleErrorHandler(s"parsing failed for markup $markup", t))

  def producer[F[_] : {Async, Console}](urlSource: URLSource, urlDownloader: URLDownloader, downloadConcurrency: Int, queue: Queue[F, Option[String]]): Stream[F, Unit] = {
    Stream
      .emits(urlSource.urls.map(Some(_)).appended(None))
      .parEvalMap(downloadConcurrency)(download(urlDownloader))
      .collect { case Right(markup) => markup }
      .evalMap(queue.offer)
  }

  def consumer[F[_] : {Async, Console}](queue: Queue[F, Option[String]], htmlParser: HTMLParser): Stream[F, List[String]] =
    consumer(queue, htmlParser, _ => Async[F].unit)

  def consumer[F[_] : {Async, Console}](queue: Queue[F, Option[String]], htmlParser: HTMLParser,
                                        sink: List[String] => F[Unit]): Stream[F, List[String]] = {
    Stream
      .fromQueueNoneTerminated(queue)
      .evalMap(parseLinks(htmlParser))
      .collect { case Right(links) => links }
      .evalTap(sink)
  }
}

