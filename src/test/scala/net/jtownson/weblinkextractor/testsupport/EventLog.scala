package net.jtownson.weblinkextractor.testsupport


import net.jtownson.weblinkextractor.testsupport.Event
import net.jtownson.weblinkextractor.testsupport.Event.*

import java.util.concurrent.atomic.AtomicReference

// Forms a serialized view of the producer and consumer events,
// so that we can see what happened when we run them.
class EventLog {
  val events: AtomicReference[Vector[Event]] = new AtomicReference(Vector.empty)

  def log(event: Event): Unit = {
    events.updateAndGet(events => events :+ event)
  }

  def isProducingAndConsumingInterleaved: Boolean = {
    val events = this.events.get()
    val firstConsumed = events.indexWhere(_.isInstanceOf[Consumed])
    val lastProduced = events.lastIndexWhere(_.isInstanceOf[Downloaded])
    // NOT interleaved if all URLs are Downloaded before any are consumed (lastDownloaded < firstConsumed)
    // interleaved if some URLs are consumed before all are Downloaded (firstConsumed < lastDownloaded)
    firstConsumed < lastProduced
  }

  def concurrentDownloads: Vector[Int] = events.get().scanLeft(0) { (acc, nextEvent) =>
    nextEvent match {
      case StartedDownload(_) => acc + 1
      case Downloaded(_) => acc - 1
      case _ => acc
    }
  }

  def maxConcurrentDownloads: Int = concurrentDownloads.max

  def consumed: Seq[Consumed] = events.get().collect { case e: Consumed => e }

  def totalConsumed: Int = consumed.size

  def downloaded: Seq[Downloaded] = events.get().collect { case e: Downloaded => e }

  def totalDownloaded: Int = downloaded.size

  def totalDownloadFailures: Int = events.get().collect { case e: DownloadFailed => e }.size

  def totalConsumeFailures: Int = events.get().collect { case e: ConsumeFailed => e }.size
}
