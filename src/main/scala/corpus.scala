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
import scala.annotation.tailrec

inline val DEBUG = false

extension [A](p: Parsley[A])
  inline def debugAs(inline name: String) =
    inline if DEBUG then p.debug(name) else p

enum LispNode[F[_]]:
  case Leaf(name: F[String])
  case Nest(title: F[String], nodes: List[LispNode[F]])

  def mapK[G[_]](f: [A] => F[A] => G[A]): LispNode[G] =
    this match
      case Leaf(name)         => Leaf(f(name))
      case Nest(title, nodes) => Nest(f(title), nodes.map(_.mapK(f)))

  def foreach(f: LispNode[F] => Unit) =
    @tailrec()
    def go(nodes: List[LispNode[F]]): Unit =
      nodes match
        case Nil =>
        case h :: next =>
          h match
            case Leaf(name) =>
              f(h)
              go(next)

            case Nest(title, rest) =>
              f(h)
              go(next ++ rest)
    end go

    go(List(this))
  end foreach
end LispNode

case class WithSpan[A](span: LSP_Range, value: A)

extension [A](p: Parsley[A])
  def wsp = p <* whitespaces
  def withSpan: Parsley[WithSpan[A]] =
    val pos: Parsley[Position] =
      Parsley.pos.map((line, col) => Position(line - 1, col - 1))

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

    val title = manyUntil(item, endOfLine).map(_.mkString)

    val header: Parsley[WithSpan[String]] =
      titleSeparator *> title.withSpan <* titleSeparator <* newline

    val codeLine =
      ((many(satisfy(isSpace)) *> noneOf('-')) <::> someUntil(item, endOfLine))
        .map(_.mkString)
        .debugAs("code line")

    val code =
      manyUntil(item, Parsley.attempt(string("---") <* newline)).map(_.mkString)

    val textcase =
      (
        header.wsp.debugAs("header"),
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
