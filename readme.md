# web-link-extractor

This is a simple Scala 3 project demonstrating an FS2/cats-effect producer/consumer pipeline with a circular buffer. 
The pipeline downloads HTML pages and extracts hyperlinks.

The project also shows a few tricks for testing concurrent apps, such as event logging. Event logging in particular is interesting because it converts temporal execution into a flat, spatial list. Temporal execution is a nightmare to visualize and reason about but, with a list of events, that's an easy task.

---

## Build & test

Requirements:
- Scala 3
- sbt

---

## Run the application

The `WebLinkExtractorApp` provides a simple `IOApp` entry point. 
By default it reads URLs from the resource file `src/main/resources/url-list.txt`.

Run the app with:

```bash
sbt run
```

Configuration points in `WebLinkExtractorApp`:
- `queueMaxSize` - the size of the queue buffer. The default in the sample app is `100`.
- `downloadConcurrency` - number of concurrent downloads.
- `urlSource` - source of URLs (resources, file, or custom list).


---

## Design notes

- Concurrency and threads:
  - Downloads are run with `parEvalMap(downloadConcurrency)` to download multiple URLs concurrently and produce markup items as they complete.
  - The implementations of `URLDownloader` and `HTMLParser` are synchronous, in order to demonstrate the use of `Async.blocking` in cats effect.

- Error handling and isolation:
  - Download and parse errors are attempted and converted to Left; failed items are filtered out so a single failed download/parse does not stop the entire pipeline.
  - Tests exercise failure scenarios to ensure isolation.

- Tests and verification:
  - Tests use an `EventLog`, which serialized the asynchronous execution and makes it easy to make assertions about what happened.
  - The repo includes ScalaTest, ScalaCheck, cats-effect, fs2 and cats-effect testing helpers. 
  - Tests are example-driven and demonstrate all the requirements in the exercise. In this sense they are comprehensive.
  - That said, there are many, many other cases that a production implementation would need to provide in order to fetch and parse online content with a decent success rate. I've assmed this is not the point of the exercise.

---

## Files to look at
- `src/main/scala/net/jtownson/weblinkextractor/WebLinkExtractorApp.scala` - sample producer/consumer application.
- `src/main/scala/net/jtownson/weblinkextractor/Fs2ProducerConsumer[Spec].scala` - core producer/consumer implementation and test cases.
- `src/main/scala/net/jtownson/weblinkextractor/URLDownloader.scala` - simple downloader traits and helpers.
- `src/main/scala/net/jtownson/weblinkextractor/HTMLParser.scala` - jsoup-based link extractor.


---

