package treesitter.lsp

import langoustine.lsp.all.*
import scala.util.Try

enum Result[+A]:
  case Get(a: A)
  case NotReady

  def toOption: Option[A] =
    this match
      case NotReady => None
      case Get(a)   => Some(a)

class State private (
    private var grammar: Option[Grammar],
    private var corpus: Option[Corpus]
):
  def identifierAt(pos: Position): Result[Option[String]] =
    ifReady { grammar =>
      grammar.text.lines.get(pos.line.value).map { l =>
        val after  = l.drop(pos.character.value)
        val before = l.take(pos.character.value)

        val idAfter = after.takeWhile(c => c.isLetterOrDigit || c == '_')
        val idBefore = before.reverse
          .takeWhile(c => c.isLetterOrDigit || c == '_')
          .reverse

        idBefore + idAfter
      }
    }

  def rules =
    ifReady { grammar =>
      grammar.rules
        .flatMap { case (rule, _) =>
          for
            locStart <- grammar.text.back(rule.position.start)
            locEnd   <- grammar.text.back(rule.position.end)
          yield rule.name -> Location(
            grammar.location,
            Range(locStart.toPosition, locEnd.toPosition)
          )
        }
        .toVector
        .sortBy(_._1)
    }

  def ruleDefinition(pos: Position) =
    ifReady { grammar =>
      for
        id       <- identifierAt(pos).toOption.flatten
        (r, _)   <- grammar.rules.find(_._1.name == id)
        locStart <- grammar.text.back(r.position.start)
        locEnd   <- grammar.text.back(r.position.end)
      yield r.name -> Location(
        grammar.location,
        Range(locStart.toPosition, locEnd.toPosition)
      )
    }

  def ruleHover(pos: Position) =
    ifReady { grammar =>
      for
        (ruleName, v) <- ruleDefinition(pos).toOption.flatten
        start = v.range.start.line.value
        end   = v.range.end.line.value
        text  = grammar.text.sliceLines(start, end)
      yield ruleName -> text
    }

  def ifReady[A](f: Grammar => A): Result[A] =
    grammar.map(f).map(Result.Get.apply).getOrElse(Result.NotReady)

  def updateGrammar(str: String, uri: DocumentUri): Either[String, Unit] =
    Try {
      synchronized {
        grammar = Some(indexGrammar(str, uri))
      }
    }.toEither.left.map(_.getMessage)

end State

object State:
  def create() =
    new State(None)
