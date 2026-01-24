package net.jtownson.weblinkextractor

import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.testing.scalatest.AsyncIOSpec
import net.jtownson.weblinkextractor.Fs2ProducerConsumer.{consumer, producer}
import net.jtownson.weblinkextractor.Fs2ProducerConsumerSpec.*
import net.jtownson.weblinkextractor.testsupport.Event.*
import net.jtownson.weblinkextractor.testsupport.{Event, EventLog}
import net.jtownson.weblinkextractor.{HTMLParser, URLDownloader, URLSource}
import org.scalacheck.Gen
import org.scalatest.Checkpoints.Checkpoint
import org.scalatest.Succeeded
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers.{the, *}

import java.net.{URI, URL}
import java.util.concurrent.CyclicBarrier
import scala.concurrent.duration.DurationInt

class Fs2ProducerConsumerSpec extends AsyncFreeSpec with AsyncIOSpec {

  "producer and consumer" - {
    "by producing 100 URLs into a cyclic queue of size 10 and seeing which URLs are consumed, we prove that" - {
      "oldest queue entries are trimmed when the queue size balloons" in {
        val nUrls = 100
        val queueSize = 11
        val eventLog = new EventLog
        val downloader = new TestDownloaderAndParser(eventLog, new CyclicBarrier(1))

        val test = for {
          queue <- Queue.circularBuffer[IO, Option[String]](queueSize) // out by one error in circularBuffer impl?

          producerFibre <-
            producer[IO](
              urlSource = URLSource.fromList(numberedUrls(nUrls)),
              urlDownloader = downloader,
              downloadConcurrency = 2,
              queue = queue
            ).compile.drain.start

          _ <- waitUntil(IO(eventLog.totalDownloaded == nUrls))

          consumerFibre <-
            consumer[IO](
              queue = queue,
              htmlParser = downloader
            ).compile.drain.start

          _ <- consumerFibre.join
        } yield {
          eventLog.consumed
        }

        test.asserting { consumed =>
          val expected = (91 to 100).map(i => Consumed(s"markup-for-http://example.com/page-$i"))
          consumed should contain theSameElementsAs expected
        }
      }
    }

    "by providing flaky downloads and parsing, we prove that" - {
      "producer and consumer complete correctly in the face of errors" in {
        val nUrls = 100
        val eventLog = new EventLog
        val downloader = new FlakyDownloaderAndParser(new TestDownloaderAndParser(eventLog, new CyclicBarrier(1)))

        val test = for {
          queue <- Queue.unbounded[IO, Option[String]]

          consumerFibre <-
            consumer[IO](
              queue = queue,
              htmlParser = downloader
            ).compile.drain.start

          producerFibre <-
            producer[IO](
              urlSource = URLSource.fromList(urls(nUrls).sample.get),
              urlDownloader = downloader,
              downloadConcurrency = 2,
              queue = queue
            ).compile.drain.start

          _ <- consumerFibre.join
        } yield {
          (eventLog.totalDownloaded, eventLog.totalDownloadFailures, eventLog.totalConsumed, eventLog.totalConsumeFailures)
        }

        test.asserting { (totalDownloaded, totalDownloadFailures, totalConsumed, totalConsumeFailures) =>
          val cp = new Checkpoint
          cp {
            totalDownloaded + totalDownloadFailures shouldBe nUrls
          }
          cp {
            totalConsumed + totalConsumeFailures shouldBe totalDownloaded
          }
          cp.reportAll()
          Succeeded
        }
      }
    }

    "by showing that queue events are consumed before the producer completes, we prove that" - {
      "producer and consumer run concurrently" in {
        val eventLog = new EventLog
        val downloader = new TestDownloaderAndParser(eventLog, new CyclicBarrier(1))

        val test: IO[Boolean] = for {
          // By using a bounded queue, the producer will block until the consumer
          // has consumed some items, so the test would deadlock if the producer
          // and consumer did not run concurrently.
          queue <- Queue.bounded[IO, Option[String]](4)

          consumerFibre <-
            consumer[IO](
              queue = queue,
              htmlParser = downloader
            ).compile.drain.start

          producerFibre <-
            producer[IO](
              urlSource = URLSource.fromList(urls(100).sample.get),
              urlDownloader = downloader,
              downloadConcurrency = 4,
              queue = queue
            ).compile.drain.start

          _ <- consumerFibre.join
        } yield {
          eventLog.isProducingAndConsumingInterleaved
        }

        test.asserting { isProducingAndConsumingInterleaved =>
          isProducingAndConsumingInterleaved shouldBe true
        }
      }
    }
  }

  "producer" - {
    "by showing that the concurrent count of DownloadStarted in the event log " +
      "is equal to the configured concurrency, we prove that" - {
      "it downloads URLs concurrently" in {
        val nUrls = 160
        val downloadConcurrency = 4
        val eventLog = new EventLog
        val downloader = new TestDownloaderAndParser(eventLog, new CyclicBarrier(downloadConcurrency))

        val test: IO[(Int, Int)] = for {
          queue <- Queue.unbounded[IO, Option[String]]

          fiber <-
            producer[IO](
              urlSource = URLSource.fromList(urls(nUrls).sample.get),
              urlDownloader = downloader,
              downloadConcurrency = downloadConcurrency,
              queue = queue
            ).compile.drain.start

          _ <- fiber.join
        } yield {
          (eventLog.maxConcurrentDownloads, eventLog.totalDownloaded)
        }

        test.asserting { (maxConcurrentDownloads, totalDownloaded) =>
          val cp = new Checkpoint
          cp(maxConcurrentDownloads shouldBe downloadConcurrency)
          cp(totalDownloaded shouldBe nUrls)
          cp.reportAll()
          Succeeded
        }
      }
    }
  }
}

object Fs2ProducerConsumerSpec {

  // Decorator to randomly fail downloads and parsing and throw exceptions
  class FlakyDownloaderAndParser(inner: TestDownloaderAndParser) extends URLDownloader with HTMLParser {
    private val random = new scala.util.Random

    override def urlMarkup(url: URL): String = {
      if (random.nextDouble() < 0.3) {
        inner.eventLog.log(DownloadFailed(url))
        throw new RuntimeException(s"download failure for $url")
      } else {
        inner.urlMarkup(url)
      }
    }

    override def extractLinks(markup: String): List[String] = {
      if (random.nextDouble() < 0.3) {
        inner.eventLog.log(ConsumeFailed(markup))
        throw new RuntimeException(s"parsing failure for $markup")
      } else {
        inner.extractLinks(markup)
      }
    }
  }

  // Test implementation of URLDownloader and HTMLParser that allows testcases to
  // force backpressure and logs the download and parsing events for subsequent test assertions.
  // the CyclicBarrier is used to create back pressure without Thread.sleep.
  class TestDownloaderAndParser(val eventLog: EventLog, val gate: CyclicBarrier) extends URLDownloader with HTMLParser {

    def events: Vector[Event] = eventLog.events.get()

    override def extractLinks(markup: String): List[String] = {
      gate.await()
      eventLog.log(Consumed(markup))
      List(s"links-extracted-from-$markup")
    }

    override def urlMarkup(url: URL): String = {
      eventLog.log(StartedDownload(url))
      try {
        gate.await()
        s"markup-for-$url"
      } finally {
        eventLog.log(Downloaded(url))
      }
    }
  }

  def numberedUrls(n: Int): List[URL] = (1 to n).map(i => URI.create(s"http://example.com/page-$i").toURL).toList

  def urls(n: Int): Gen[List[URL]] = Gen.listOfN(n, url)

  val url: Gen[URL] = for {
    proto <- Gen.oneOf("http", "https", "ftp")
    host <- Gen.alphaLowerStr.suchThat(_.nonEmpty)
  } yield URI.create(s"$proto://$host").toURL

  def waitUntil(cond: IO[Boolean]): IO[Unit] =
    cond.ifM(
      IO.unit,
      IO.sleep(1.millis) *> waitUntil(cond)
    )
}