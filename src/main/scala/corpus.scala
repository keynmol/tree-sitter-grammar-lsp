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

enum LispNode[F[_]]:
  case Leaf(name: F[String])
  case Nest(title: F[String], nodes: List[LispNode[F]])

case class WithSpan[A](span: LSP_Range, value: A)

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
  export parsers.fully as parser

  def apply[F[_]](nm: F[String], subnodes: List[LispNode[F]]) =
    subnodes match
      case head :: next => LispNode.Nest(nm, subnodes)
      case Nil          => LispNode.Leaf(nm)

  private object parsers:
    val nodeName: Parsley[WithSpan[String]] = stringOfSome(
      letterOrDigit <|> char('_')
    ).withSpan.debug("nodeName")

    lazy val innerParser: Parsley[List[LispNode[WithSpan]]] =
      val rec = many(parser.wsp).debug("rec")

      val simple = nodeName.wsp
        .map(LispNode.Leaf(_))
        .map(List(_))
        .debug("simple")

      simple | rec
    end innerParser

    lazy val parser: parsley.Parsley[LispNode[WithSpan]] =
      char('(') *>
        (nodeName.wsp, innerParser).zipped(apply) <*
        char(')')

    lazy val fully = parser.wsp

  end parsers
end LispNode

case class TextCase[F[_]](title: F[String], code: String, expected: LispNode[WithSpan])
object TextCase:
  export parsers.textcase as parser

  private object parsers:
    val titleSeparator = someUntil(char('='), endOfLine).void

    val titleLine =
      (noneOf('=') <::> someUntil(item, endOfLine)).map(_.mkString)

    val title = sepEndBy1(titleLine, endOfLine).map(_.mkString("\n"))

    val header: Parsley[String] =
      titleSeparator *> title <* titleSeparator <* newline

    val codeLine = ((many(satisfy(isSpace)) *> noneOf('-')) <::> someUntil(item, endOfLine)).map(_.mkString).debug("code line")
    val code = 
      many(codeLine).map(_.mkString("\n")) <* string("---").wsp

    val textcase =
      (header.withSpan.wsp.debug("header"), code.debug("code"), LispNode.parser)
        .zipped(TextCase.apply)

  end parsers
end TextCase

case class CorpusFile(cases: List[TextCase[WithSpan]])

object CorpusFile:
  val parser =
    manyUntil(TextCase.parser.debug("Textcase parser"), eof.debug("end of file")).map(CorpusFile.apply)

case class Corpus(items: Map[DocumentPath, CorpusFile])
