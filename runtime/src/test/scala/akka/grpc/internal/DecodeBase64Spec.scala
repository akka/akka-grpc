package akka.grpc.internal

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalatest.wordspec.AnyWordSpecLike

class DecodeBase64Spec extends TestKit(ActorSystem()) with AnyWordSpecLike {

  private val data = ByteString(Range(-128, 128).map(_.toByte).toArray)

  "DecodeBase64" should {
    "handle a single element" in {
      Source
        .single(data.encodeBase64)
        .via(DecodeBase64())
        .runWith(TestSink[ByteString]())
        .request(1)
        .expectNext(data)
        .expectComplete()
    }

    "handle a chunked stream" in {
      val encodedData = data.encodeBase64
      for (i <- Range(1, 12)) {
        val chunks = encodedData.grouped(i).to[scala.collection.immutable.Seq]
        Source(chunks)
          .via(DecodeBase64())
          .fold(ByteString.empty)(_.concat(_))
          .runWith(TestSink[ByteString]())
          .request(1)
          .expectNext(data)
          .expectComplete()
      }
    }

    "handle a chunked stream with mid-stream flushes" in {
      for (i <- Range(1, 9)) {
        val chunks = data.grouped(i).to[scala.collection.immutable.Seq]
        Source(chunks.map(_.encodeBase64))
          .via(DecodeBase64())
          .runWith(TestSink[ByteString]())
          .request(chunks.length)
          .expectNextN(chunks)
          .expectComplete()
      }
    }
  }
}
