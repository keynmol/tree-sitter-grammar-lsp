package treesitter.lsp

import treesitter.lsp.corpus.LispNode
import concurrent.ExecutionContext.Implicits.global
import langoustine.lsp.structures.Position
import treesitter.lsp.corpus.CorpusFile

object ServerLogicTests extends weaver.FunSuite:
  test("semantic tokens index") {

    matches(
      corpusFile(
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
       |
       |=======================================
       |Bla bla bla
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
      )
    ) { case Right(file -> str) =>
      val logic   = ServerLogic(State.create())
      val encoded = file.cases.toVector.flatMap(logic.indexSemanticTokens)

      val byPos =
        val builder = Map.newBuilder[Position, String]

        file.cases.foreach { node =>
          node.expected.foreach {
            case LispNode.Leaf(name) =>
              builder += name.span.start -> name.value
            case LispNode.Nest(title, nodes) =>
              builder += title.span.start -> title.value
          }

          builder += node.title.span.start -> node.title.value
        }

        builder.result
      end byPos

      forEach(encoded) { tok =>
        val title = byPos(tok.position)

        expect.same(title, str.slice(tok.position, tok.length.value))
      }

    }

  }

  def corpusFile(str: String) =
    CorpusFile.parser.parse(str).toEither.map(_ -> str)
end ServerLogicTests
