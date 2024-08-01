import java.nio.file.{Path, Paths}
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, IOResult, OverflowStrategy}
import akka.stream.scaladsl.{Compression, FileIO, Flow, Framing, Keep, RunnableGraph, Sink, Source}
import akka.util.ByteString
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._

case class Package(name: String, stars: Int, tests: Double, releases: Int, sumCommitsTop3: Int)

object PackageStreamAnalyzer extends App {
  implicit val system: ActorSystem = ActorSystem("PackageStreamAnalyzer")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  // Step 1: Get the path of the gzip file
  val gzipFilePath: Path = Paths.get("src/main/resources/packages.txt.gz")

  // Step 2: Define a function to request package metadata from the NPMS API
  def getPackageMetadata(packageName: String): Package = {
    val url = s"https://api.npms.io/v2/package/$packageName"

    val response = requests.get(url)

    // Parse the JSON response and extract the required metadata fields
    val parsedJson = ujson.read(response.text())

    // Extract the star count from the JSON data under the "npm" field
    val myStars = parsedJson("collected")("github")("starsCount").num.toInt

    // Extract the test score from the JSON data under the "quality" field in the "evaluation" section
    val myTests = parsedJson("evaluation")("quality")("tests").num.toDouble

    // Extract the releases from the JSON data under the "metadata" field and collect into an array
    val releases = parsedJson("collected")("metadata")("releases").arr
    // Extracting all "count" values from these array of releases
    val counts1 = releases.map(release => release("count").num.toInt)
    // Calculate the sum of those count
    val mySumOfReleases = counts1.sum

    // Extract the contributors from the JSON data under the "github" field and collect into an array
    val contributors = parsedJson("collected")("github")("contributors").arr
    // Extracting all "commitsCount" values from these array of contributors
    val counts2 = contributors.map(contributor => contributor("commitsCount").num.toInt)
    // Sort the counts2 array in descending order
    val sortedCounts2 = counts2.sorted.reverse
    // Get the top 3 values from the sortedCounts2 array and calculate their sum
    val mySumOfTop3CommitsCount = sortedCounts2.take(3).sum

    Package(packageName, myStars, myTests, mySumOfReleases, mySumOfTop3CommitsCount)
  }

  // Step 3: Create the source that reads the compressed file
  val source: Source[ByteString, Future[IOResult]] = FileIO.fromPath(gzipFilePath)

  // Step 4: Create the flow to split the compressed file into package names
  val flowSplitPerLine: Flow[ByteString, ByteString, NotUsed] =
    Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024, allowTruncation = true)

  // Step 5: Create the flow to convert ByteString to String (package names)
  val flowStringCase: Flow[ByteString, String, NotUsed] =
    Flow[ByteString].map(_.utf8String)

  // Step 6: Define the buffering and backpressure strategy
  val bufferSize = 25
  val backpressureStrategy = OverflowStrategy.backpressure

  // Step 7: Create the flow for package metadata retrieval
  val packageMetadataFlow: Flow[String, Package, NotUsed] =
    Flow[String]
      .map(getPackageMetadata)
      .buffer(bufferSize, backpressureStrategy)

  // Step 8: Define the sink to print the package metadata
  val printSink =
    Sink.foreach(packageData => println(packageData))

  // Step 9: Define the sink to save the package metadata to an external file
  val filePath = "result.txt"
  val fileSink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(Paths.get(filePath))

  // Step 10: Define the flow to convert Package to ByteString
  val packageToByteStringFlow: Flow[Package, ByteString, NotUsed] =
    Flow[Package]
      .map(p => ByteString(s"Package Name: ${p.name}, Stars: ${p.stars}, Tests: ${p.tests}, Releases: ${p.releases}, Sum of Top 3 Commits: ${p.sumCommitsTop3}\n"))

  // Step 11: Build and run the runnable graph for both sinks using alsoTo
  val runnableGraph =
    source.via(Compression.gunzip())
      .via(flowSplitPerLine)
      .via(flowStringCase)
      .throttle(elements = 1, new FiniteDuration(2, SECONDS))
      .via(packageMetadataFlow)
      .filter(p => p.stars >= 20 && p.tests >= 0.5 && p.releases >= 2 && p.sumCommitsTop3 >= 150)
      .alsoToMat(printSink)(Keep.right)
      .via(packageToByteStringFlow)
      .toMat(fileSink)(Keep.right)

  // Step 12: Run the graph and handle the completion or failure
  val result: Future[IOResult] = runnableGraph.run()

  // Step 13: Handle completion or failure of the stream
  result.onComplete { _ =>
    system.terminate()
  }
}
//
