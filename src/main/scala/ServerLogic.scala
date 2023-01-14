package treesitter.lsp

import langoustine.lsp.all.*
import langoustine.lsp.LSPBuilder
import cats.effect.IO
import jsonrpclib.fs2.*
import opaque_newtypes.*
import io.scalajs.nodejs.fs.*
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import java.net.URI
import java.nio.file.Paths
import io.scalajs.nodejs.url.URL
import io.scalajs.nodejs.path.Path as JSPath
import langoustine.lsp.tools.*
import util.chaining.*
import scala.annotation.tailrec

class ServerLogic(state: State)(using ExecutionContext):
  def detectFile(path: DocumentPath): GrammarFile =
    state.getRoot
      .map { rootPath =>
        val relative = JSPath.relative(rootPath.value, path.value)
        if relative == "grammar.js" then GrammarFile.Spec
        else if path.ext == ".txt" && JSPath.dirname(relative) == "corpus" then
          GrammarFile.CorpusFile(path.filename)
        else GrammarFile.Unknown(relative)
      }
      .getOrElse(GrammarFile.Unknown(path.value))

  def handleFile(path: DocumentPath, mode: Mode) =
    inline def read = Fs.readFileFuture(path.value, "utf-8")

    detectFile(path) match
      case GrammarFile.Spec =>
        read.map(state.updateGrammar(_, path.uri)).collect { case Left(err) =>
          scribe.error(s"Error updating grammar state: `$err`")
        }
      case GrammarFile.CorpusFile(nm) =>
        read
          .map(state.indexCorpusFile(_, path))
          .collect { case Left(err) =>
            scribe.error(s"Error indexing corpus file `$path`: `$err`")
          }

      case GrammarFile.Unknown(pth) =>
        Future {
          scribe.error(s"Unknown path opened: `$pth`")
        }

    end match
  end handleFile

  import corpus.*

  def indexSemanticTokens(tc: TextCase[WithSpan]): Vector[SemanticToken] =
    val tokens = Vector.newBuilder[SemanticToken]

    tc.expected.foreach {
      case LispNode.Nest(title, nodes) =>
        tokens += SemanticToken.fromRange(title.span, SemanticTokenTypes.`type`)
      case LispNode.Leaf(title) =>
        tokens += SemanticToken.fromRange(title.span, SemanticTokenTypes.`type`)
    }

    tokens += SemanticToken(
      tc.title.span.start,
      uinteger(tc.title.value.trim.length),
      SemanticTokenTypes.namespace
    )

    tokens.result()
  end indexSemanticTokens
end ServerLogic

