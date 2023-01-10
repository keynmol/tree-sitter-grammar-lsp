package treesitter.lsp

import typings.acorn.mod.Node

import langoustine.lsp.all.*

case class Grammar(
    rules: Map[Rule, Reductions],
    text: TextIndex,
    location: DocumentUri
)

case class Rule(name: String, position: Pos)

case class Pos(start: Int, end: Int)
object Pos:
  def of(n: Node) =
    Pos(n.start.toInt, n.end.toInt)

case class Mention(name: String, pos: Pos)

case class Reductions(mentions: List[Mention]):
  def ++(other: Reductions) = copy(mentions = mentions ++ other.mentions)

object Reductions:
  def of(o: Option[Mention]) = Reductions(o.toList)

case class Loc(line: Int, char: Int):
  def toPosition = Position(line, char)

case class TextIndex(lines: Map[Int, String], back: Int => Option[Loc]):

  def point(line: Int): Unit = point(line, None)
  def point(loc: Loc): Unit  = point(loc.line, Some(loc.char))

  def sliceLines(start: Int, end: Int) =
    (safeLineIdx(start) to safeLineIdx(end)).flatMap(lines.get).mkString("\n")

  private inline def safeLineIdx(n: Int) =
    (n max 0) min (lines.size - 1)

  private def point(line: Int, char: Option[Int]): Unit =
    inline def highlight(l: String) =
      char match
        case None => l
        case Some(c) =>
          val (bef, aft) = l.splitAt(c)
          bef + Console.RED + l(c) + Console.RESET + aft.drop(1)

    val start = (line - 5) max 0
    val end   = (line + 5) min (lines.size - 1)
    (start to end).foreach { i =>
      val s =
        val t = i.toString
        (" " * (3 - t.length)) + t

      lines.get(i).foreach { lineStr =>
        if i == line then println(">>> '" + highlight(lineStr) + "'")
        else println(s + " '" + lineStr + "'")
      }

    }
  end point

end TextIndex
object TextIndex:
  def of(str: String) =
    val indexed =
      str.linesIterator.zipWithIndex.map(_.swap)
    val (offsetsReveresed, _) =
      str.linesIterator.foldLeft(List.empty[Int] -> 0) {
        case ((offsets, acc), str) =>
          (acc :: offsets) -> (acc + str.length + 1)
      }

    val offsets =
      offsetsReveresed.reverse.zipWithIndex.toVector

    TextIndex(
      indexed.toMap,
      idx =>
        offsets.find((off, _) => off >= idx).map { (_, linePlusOne) =>
          val (offset, line) = offsets.apply(linePlusOne - 1)
          Loc(line, idx - offset)
        }
    )

  end of
end TextIndex
