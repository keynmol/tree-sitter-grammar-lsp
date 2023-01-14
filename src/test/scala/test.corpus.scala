package treesitter.lsp

import corpus.*

import weaver.*
import cats.Show
import LispNode.*
import cats.effect.IO
import scala.concurrent.duration.*

def matches[A](x: A)(
    f: PartialFunction[A, Expectations]
)(implicit
    pos: SourceLocation,
    A: Show[A] = Show.fromToString[A]
): Expectations =
  if f.isDefinedAt(x) then f(x)
  else Expectations.Helpers.failure("Pattern did not match, got: " + A.show(x))

object CorpusTest extends weaver.FunSuite:
  test("lisp node only") {
    def parsed(t: String) =
      LispNode.parser.parse(t).toEither

    expect.all(
      parsed("(test (a b) )").isRight,
      parsed("(test (a\n b) \n)").isRight,
      parsed("(test    \n(a\n     b) \n)").isRight,
      parsed("""
       |(compilation_unit
       |  (a b)
       |  (test (c a)))""".trim.stripMargin).isRight,
      parsed("(test a)").isRight
    ) &&
    expect.all(
      parsed("(test   (a b").isLeft,
      parsed("(!test   (a b))").isLeft,
      parsed("a b").isLeft,
      parsed("(a").isLeft
    )

  }

  test("Dodgy code") {
    val text =
      """
       |=======================================
       |Identifiers
       |=======================================
       |
       |val x = y match {
       |  case a @ B(1) => a
       |  case b @ C(d @ (e @ X, _: Y)) => e
       |  case req @ (POST | GET) -> Root / "test" => 5
       |}
       |
       |---
       |
       |(compilation_unit)
        """.trim.stripMargin

    matches(CorpusFile.parser.parse(text).toEither) { case Right(result) =>
      matches(result.cases.headOption) { case Some(tc) =>
        success
      }
    }
  }

  test("Entire test case") {
    val text =
      """
       |=======================================
       |Identifiers
       |=======================================
       |
       |inline def m =
       |  var hello = 1
       |def test = 1
       |
       |---
       |
       |(compilation_unit
       |  (a b)
       |  (test (c a)))
        """.trim.stripMargin

    matches(CorpusFile.parser.parse(text).toEither) { case Right(result) =>
      matches(result.cases.headOption) { case Some(tc) =>
        expect.same(tc.title.value, "Identifiers") &&
        expect.same(
          tc.code.linesIterator
            .map(_.stripTrailing())
            .toList
            .filter(_.nonEmpty),
          List(
            "inline def m =",
            "  var hello = 1",
            "def test = 1"
          )
        ) &&
        expect(
          tc.expected.mapK[Id]([A] => (ws: WithSpan[A]) => ws.value) ==
            Nest(
              "compilation_unit",
              List(
                Nest("a", List(Leaf("b"))),
                Nest("test", List(Nest("c", List(Leaf("a")))))
              )
            )
        )
      // expect.same(text.slice(tc.title.span.start))
      }

    }
  }

  test("Multiple test cases") {
    val N = 5
    def code(n: Int) = s"""
        |def code = $n
        |object Obj$n{
        |  val state = $n
        |}""".trim.stripMargin

    val text = List
      .tabulate(N) { n =>
        s"""
        |=======================================
        |Case $n
        |=======================================
        |
        |${code(n)}
        |
        |---
        |
        |(compilation_unit
        |  (test$n a)
        |)
        """.trim.stripMargin
      }
      .mkString("\n\n")

    matches(CorpusFile.parser.parse(text).toEither) {
      case Right(CorpusFile(cases)) =>
        expect.same(
          cases.map(_.title.value),
          List.tabulate(N)(n => s"Case $n")
        ) &&
        expect.same(
          cases.map(_.expected.withoutSpans),
          List.tabulate(N)(n =>
            Nest("compilation_unit", Nest(s"test$n", Leaf("a") :: Nil) :: Nil)
          )
        ) &&
        forEach(cases.map(_.code).zipWithIndex) { case (cd, i) =>
          expect.same(cd.trim, code(i))
        }

    }
  }
end CorpusTest
