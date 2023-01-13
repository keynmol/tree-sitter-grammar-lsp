package treesitter.lsp
package corpus

import parsley.character.*
import parsley.debug.*
import parsley.combinator.*
import parsley.Parsley
import parsley.lift
import parsley.implicits.lift.*
import parsley.implicits.zipped.*

import langoustine.lsp.all.Range as LSP_Range
import langoustine.lsp.structures.Position
import langoustine.lsp.structures.Range

inline val DEBUG = false

enum LispNode[F[_]]:
  case Leaf(name: F[String])
  case Nest(title: F[String], nodes: List[LispNode[F]])

  def mapK[G[_]](f: [A] => F[A] => G[A]): LispNode[G] =
    this match
      case Leaf(name)         => Leaf(f(name))
      case Nest(title, nodes) => Nest(f(title), nodes.map(_.mapK(f)))

case class WithSpan[A](span: LSP_Range, value: A)

extension [A](p: Parsley[A])
  inline def debugAs(inline name: String) =
    inline if DEBUG then p.debug(name) else p

extension [A](p: Parsley[A])
  def wsp = p <* whitespaces
  def withSpan: Parsley[WithSpan[A]] =
    val pos: Parsley[Position] =
      Parsley.pos.map(Position.apply)

    (
      pos,
      p,
      pos
    ).zipped((start, value, end) => WithSpan(Range(start, end), value))
  end withSpan
end extension

object LispNode:
  type Id[A] = A
  extension (n: LispNode[WithSpan])
    def withoutSpans: LispNode[Id] =
      n.mapK[Id]([A] => (ws: WithSpan[A]) => ws.value)

  export parsers.fully as parser

  def apply[F[_]](nm: F[String], subnodes: List[LispNode[F]]) =
    subnodes match
      case head :: next => LispNode.Nest(nm, subnodes)
      case Nil          => LispNode.Leaf(nm)

  private object parsers:
    val nodeName: Parsley[WithSpan[String]] = stringOfSome(
      letterOrDigit <|> char('_')
    ).withSpan.debugAs("nodeName")

    lazy val innerParser: Parsley[List[LispNode[WithSpan]]] =
      val rec = many(parser.wsp).debugAs("rec")

      val simple = nodeName.wsp
        .map(LispNode.Leaf(_))
        .map(List(_))
        .debugAs("simple")

      simple | rec
    end innerParser

    lazy val parser: parsley.Parsley[LispNode[WithSpan]] =
      char('(') *>
        (nodeName.wsp, innerParser).zipped(apply) <*
        char(')')

    lazy val fully = parser.wsp

  end parsers
end LispNode

case class TextCase[F[_]](
    title: F[String],
    code: String,
    expected: LispNode[WithSpan]
)
object TextCase:
  export parsers.textcase as parser

  private object parsers:
    val titleSeparator = someUntil(char('='), endOfLine).void

    val titleLine =
      (noneOf('=') <::> someUntil(item, endOfLine)).map(_.mkString)

    val title = sepEndBy1(titleLine, endOfLine).map(_.mkString("\n"))

    val header: Parsley[String] =
      titleSeparator *> title <* titleSeparator <* newline

    val codeLine =
      ((many(satisfy(isSpace)) *> noneOf('-')) <::> someUntil(item, endOfLine))
        .map(_.mkString)
        .debugAs("code line")

    val code =
      manyUntil(item, string("---") <* newline).map(_.mkString)

    val textcase =
      (
        header.withSpan.wsp.debugAs("header"),
        code.debugAs("code").wsp,
        LispNode.parser
      )
        .zipped(TextCase.apply)

  end parsers
end TextCase

case class CorpusFile(cases: List[TextCase[WithSpan]])

object CorpusFile:
  val parser =
    manyUntil(
      TextCase.parser.debugAs("Textcase parser"),
      eof
    ).map(CorpusFile.apply).debugAs("Corpus file")

case class Corpus(items: Map[DocumentPath, CorpusFile])
