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

object CorpusTest extends weaver.SimpleIOSuite:
  def dodgyTest(name: TestName)(f: => Expectations) =
    test(name) {
      IO(f)//.timeout(1.second)
    }

  // dodgyTest("lisp node only") {
  //   def parsed(t: String) =
  //     LispNode.parser.parse(t).toEither

  //   expect.all(
  //     parsed("(test (a b) )").isRight,
  //     parsed("(test (a\n b) \n)").isRight,
  //     parsed("(test    \n(a\n     b) \n)").isRight,
  //     parsed("""
  //      |(compilation_unit
  //      |  (a b) 
  //      |  (test (c a)))""".trim.stripMargin).isRight,
  //     parsed("(test a)").isRight
  //   ) &&
  //   expect.all(
  //     parsed("(test   (a b").isLeft,
  //     parsed("(!test   (a b))").isLeft,
  //     parsed("a b").isLeft,
  //     parsed("(a").isLeft
  //   )

  // }

  dodgyTest("Entire test case") {
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

    println(TextCase.parser.parse(text))

    matches(CorpusFile.parser.parse(text).toEither) { case Right(result) =>
      matches(result.cases.headOption) { case Some(tc) =>
        expect.same(tc.title, "Identifiers") &&
        expect.same(
          tc.code.linesIterator.map(_.stripTrailing()).toList,
          List(
            "inline def m =",
            "  var hello = 1",
            "def test = 1"
          )
        ) &&
        expect.same(
          tc.expected,
          Nest(
            "compilation_unit",
            List(
              Nest("a", List(Leaf("b"))),
              Nest("test", List(Nest("c", List(Leaf("a")))))
            )
          )
        )
      }

    }
  }

  // dodgyTest("Multiple test cases") {
  //   val N = 5
  //   def code(n: Int) = s"""
  //       |def code = $n
  //       |object Obj$n{
  //       |  val state = $n
  //       |}""".trim.stripMargin

  //   val text = List
  //     .tabulate(N) { n =>
  //       s"""
  //       |=======================================
  //       |Case $n
  //       |=======================================
  //       |
  //       |${code(n)}
  //       |
  //       |---
  //       |
  //       |(compilation_unit
  //       |  (test$n a)
  //       |)
  //       """.trim.stripMargin
  //     }
  //     .mkString("\n\n")

  //   matches(CorpusFile.parser.parse(text).toEither) {
  //     case Right(CorpusFile(cases)) =>
  //       expect.same(cases.map(_.title), List.tabulate(N)(n => s"Case $n")) &&
  //       expect.same(
  //         cases.map(_.expected),
  //         List.tabulate(N)(n =>
  //           Nest("compilation_unit", Nest(s"test$n", Leaf("a") :: Nil) :: Nil)
  //         )
  //       ) &&
  //       expect.same(cases.map(_.code), List.tabulate(N)(code))

  //   }
  // }
end CorpusTest
