package akka.grpc.internal

import akka.grpc.internal.HttpTranscoding.PathTemplateParser
import akka.grpc.internal.HttpTranscoding.PathTemplateParser.FieldPath
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher._
import org.scalactic.source.Position
import org.scalatest.Inside
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PathTemplateMatcherSpec extends AnyWordSpec with Matchers with Inside {

  private def matchUri(template: String, path: Path) = PathTemplateParser.parseToTemplate(template).matcher(path)

  private def success(
      template: String,
      path: Path,
      expectedVariables: List[(HttpTranscoding.PathTemplateParser.FieldPath, String)] = List.empty,
      expectedVerb: Option[String] = None)(implicit pos: Position) = {
    inside(matchUri(template, path)) {
      case Matched(Path.Empty, (variables, verb)) =>
        variables shouldBe expectedVariables
        verb shouldBe expectedVerb
    }
  }

  private def fail(template: String, path: Path)(implicit pos: Position) = {
    inside(matchUri(template, path)) {
      case Unmatched => succeed
    }
  }

  "path template matcher" should {

    "match simple path" in {
      val template = "/v1/shelves"
      val path = Path("/v1/shelves")

      success(template, path)
    }

    "match singleSegmentMatcher" in {
      val template = "/v1/*/buy"
      val path = Path("/v1/cars/buy")

      success(template, path)
    }

    "match multiSegmentMatcher" in {
      val template = "/v1/**"
      val path = Path("/v1/cars/buy/whatever")

      success(template, path)
    }

    "match simple variable" in {
      val template = "/v1/shelves/{id}"
      val path = Path("/v1/shelves/1")

      success(template, path, List(FieldPath(Seq("id")) -> "1"))
    }

    "match simple singleSegmentMatcher inside variable" in {
      val template = "/v1/shelves/{id=*}"
      val path = Path("/v1/shelves/1")

      success(template, path, List(FieldPath(Seq("id")) -> "1"))
    }

    "match multiple singleSegmentMatchers inside variable" in {
      val template = "/v1/{parent=language/*}/author/{name}"
      val path = Path("/v1/language/scala/author/martin")

      success(template, path, List(FieldPath(Seq("parent")) -> "language/scala", FieldPath(Seq("name")) -> "martin"))
    }

    "match simple multiSegmentsMatcher inside variable" in {
      val template = "/v1/{path=**}"
      val path = Path("/v1/language/scala/author/martin")

      success(template, path, List(FieldPath(Seq("path")) -> "language/scala/author/martin"))
    }

    "match singleSegmentMatcher and multiSegmentsMatcher inside variable" in {
      val template = "/v1/{path=book/*/**}"
      val path = Path("/v1/book/a/author/b")

      success(template, path, List(FieldPath(Seq("path")) -> "book/a/author/b"))
    }

    "match verb" in {
      val template = "/v1/shelves:GET"
      val path = Path("/v1/shelves:GET")

      success(template, path, expectedVerb = Some("GET"))
    }

    "match singleSegmentMatcher follows verb" in {
      val template = "/v1/*:GET"
      val path = Path("/v1/shelves:GET")

      success(template, path, expectedVerb = Some("GET"))
    }

    "match multiSegmentsMatcher follows verb" in {
      val template = "/v1/**:GET"
      val path = Path("/v1/shelves/buy:GET")

      success(template, path, expectedVerb = Some("GET"))
    }

    "reject path without leading slash" in {
      val template = "/v1/shelves"
      val path = Path("v1/shelves")

      fail(template, path)
    }

  }

}
