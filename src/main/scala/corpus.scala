package treesitter.lsp

import parsley.character.*
import parsley.debug.*
import parsley.combinator.*
import parsley.Parsley

enum LispNode:
  case Leaf(name: String)
  case Nest(title: String, nodes: List[LispNode])

object LispNode:
  export parsers.parser

  private object parsers:
    def constructNode(nm: String, subnodes: List[LispNode]) =
      subnodes match
        case head :: next => LispNode.Nest(nm, subnodes)
        case Nil          => LispNode.Leaf(nm)

    val nodeName = some(letter <|> char('_')).map(_.mkString)

    extension [A](p: => Parsley[A]) def wsp = p <* whitespaces

    lazy val parser: parsley.Parsley[LispNode] = (
      char('(') *>
        (nodeName.wsp <~> {

          val rec = many(parser)

          val simple = nodeName
            .map(LispNode.Leaf(_))
            .map(List(_))

          simple | rec

        }).map(constructNode) <*
        char(')').wsp
    )
  end parsers
end LispNode

case class TextCase(title: String, code: String, expected: LispNode)
object TextCase:
  export parsers.{textcase as parser, emptyLines}

  private object parsers:
    val titleSeparator = someUntil(char('='), endOfLine).void

    val titleLine =
      (noneOf('=') <::> someUntil(item, endOfLine)).map(_.mkString)

    val title = sepEndBy1(titleLine, endOfLine).map(_.mkString("\n"))

    val header =
      titleSeparator *> title <* titleSeparator

    val codeLine = (noneOf('-') <::> many(satisfy(_ != '\n')))
      .map(_.mkString)

    val code = sepEndBy(codeLine.debug("code line"), some(endOfLine))
      .map(_.mkString("\n"))

    val codeSeparator = string("---") *> manyUntil(char('-'), endOfLine)

    val emptyLines =
      sepEndBy1(many(manyUntil(whitespace, endOfLine).void), endOfLine).void

    val textcase =
      ((header <*
        emptyLines) <~>
        (code <*
          emptyLines <*
          codeSeparator) <~>
        (emptyLines *> LispNode.parser)).map { case ((title, code), node) =>
        TextCase(title, code, node)
      }
  end parsers
end TextCase

case class CorpusFile(cases: List[TextCase])

object CorpusFile:
  val parser =
    sepEndBy(TextCase.parser, TextCase.emptyLines).map(CorpusFile.apply) <* eof

case class Corpus(items: Map[langoustine.lsp.all.DocumentUri, CorpusFile])
