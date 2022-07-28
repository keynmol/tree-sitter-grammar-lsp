package grammarsy 

import typings.acorn.mod.Node

case class Grammar(
    rules: Map[Rule, Reductions]
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
