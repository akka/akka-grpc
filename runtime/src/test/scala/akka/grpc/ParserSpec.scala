package akka.grpc

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import akka.grpc.internal.HttpTranscoding.PathTemplateParser
import akka.grpc.internal.HttpTranscoding.PathTemplateParser._
import org.scalatest.EitherValues

class ParserSpec extends AnyWordSpec with Matchers with EitherValues {

  "http path template parser" should {
    "accept simple path" in {
      // /v1/shelf
      val template = Template(Seq(LiteralSegment("v1"), LiteralSegment("shelf")), None)
      PathTemplateParser.parse(template.toString).value shouldBe template
    }

    "accept singleSegmentMatcher" in {
      // /*/shelf
      val template = Template(Seq(SingleSegmentMatcher, LiteralSegment("shelf")), None)
      PathTemplateParser.parse(template.toString).value shouldBe template
    }

    "accept multiSegmentMatcher" in {
      // /v1/**
      val template = Template(Seq(LiteralSegment("v1"), MultiSegmentMatcher), None)
      PathTemplateParser.parse(template.toString).value shouldBe template
    }

    "accept singleSegmentMatcher mixed with multiSegmentMatcher" in {
      // /v1/shelves/*/**
      val template =
        Template(Seq(LiteralSegment("v1"), LiteralSegment("shelves"), SingleSegmentMatcher, MultiSegmentMatcher), None)
      PathTemplateParser.parse(template.toString).value shouldBe template
    }

    "accept variable with segments" in {
      // /v1/{parent=shelves/*}/books
      val template = Template(
        Seq(
          LiteralSegment("v1"),
          VariableSegment(FieldPath(Seq("parent")), Some(Seq(LiteralSegment("shelves"), SingleSegmentMatcher))),
          LiteralSegment("books")),
        None)
      PathTemplateParser.parse(template.toString).value shouldBe template
    }

    "accept variable without segments" in {
      // /v1/{message_id}
      val template = Template(Seq(LiteralSegment("v1"), VariableSegment(FieldPath(Seq("message_id")), None)), None)
      PathTemplateParser.parse(template.toString).value shouldBe template
    }

    "accept verb" in {
      // /v1/shelves:GET
      val template = Template(Seq(LiteralSegment("v1"), LiteralSegment("shelves")), Some("GET"))
      PathTemplateParser.parse(template.toString).value shouldBe template
    }

    "accept verb with multiSegmentMatcher" in {
      // /v1/shelves/**:GET
      val template = Template(Seq(LiteralSegment("v1"), LiteralSegment("shelves"), MultiSegmentMatcher), Some("GET"))
      PathTemplateParser.parse(template.toString).value shouldBe template
    }

    "reject multiple multiSegmentMatchers in different variables" in {
      PathTemplateParser.parse("/shelves/{id=message/**}/{theme=message/**}").left
    }

    "reject multiple multiSegmentMatchers in path" in {
      PathTemplateParser.parse("/shelves/**/**").left
    }
  }

}
