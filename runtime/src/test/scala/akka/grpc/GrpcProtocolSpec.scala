package akka.grpc
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.MediaType.Compressible
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues
import org.scalatest.prop.TableDrivenPropertyChecks._

class GrpcProtocolSpec extends AnyWordSpec with Matchers with OptionValues {

  val protoCombinations = GrpcProtocol.protocols.flatMap { p =>
    GrpcProtocol.formats.map(f => (p, f, s"application/${p.subType}+${f.name}"))
  }
  val protoImplicitCombinations = GrpcProtocol.protocols.map(p => (p, s"application/${p.subType}"))

  "contentType" should {
    "Produce expected content type" in {
      forAll(Table(("Protocol", "Format", "MediaType String"), protoCombinations: _*)) { (p, f, mediaType) =>
        p.contentType(f).mediaType.value shouldEqual mediaType
      }
    }
  }

  "Mediatype detection" should {

    "Detect explicit media types" in {
      forAll(Table(("Protocol", "Format", "MediaType String"), protoCombinations: _*)) { (p, f, _) =>
        val detected = GrpcProtocol.detect((p, f))
        detected.value._1 shouldBe p
        detected.value._2 shouldBe f
      }
    }

    "Detect implicit media types" in {
      forAll(Table(("Protocol", "MediaType String"), protoImplicitCombinations: _*)) { (p, _) =>
        val detected = GrpcProtocol.detect(p)
        detected.value._1 shouldBe p
        detected.value._2 shouldBe ProtobufSerialization.Protobuf
      }
    }

    "Not detect invalid media types" in {
      Seq(
        "nomatch/notreally",
        "text/grpc",
        "application/something",
        "application/grp",
        "application/grpcX",
        "application/grpc-webX",
        "application/grpc-web-textX").foreach { m => GrpcProtocol.detect(m) shouldBe None }
    }

    "Not detect invalid formats" in {
      GrpcProtocol.protocols.flatMap(p => Seq("", "notathing").map((p, _))).foreach { spec =>
        GrpcProtocol.detect(spec) shouldBe None
      }
    }
  }

  implicit def toMediaTypeProto(p: GrpcProtocol): MediaType = MediaType.applicationBinary(s"${p.subType}", Compressible)

  implicit def toMediaTypeStr(spec: (GrpcProtocol, String)): MediaType = spec match {
    case (p, s) => MediaType.applicationBinary(s"${p.subType}+${s}", Compressible)
  }

  implicit def toMediaTypeSer(spec: (GrpcProtocol, ProtobufSerialization)): MediaType = spec match {
    case (p, s) => MediaType.applicationBinary(s"${p.subType}+${s.name}", Compressible)
  }

  implicit def toMediaType(mType: String): MediaType = MediaType.parse(mType) match {
    case Right(mediatype) => mediatype
    case _                => fail(s"'$mType' is not a valid media type")
  }

}
