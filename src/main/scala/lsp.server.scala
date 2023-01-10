package treesitter.lsp

import langoustine.lsp.all.*
import langoustine.lsp.LSPBuilder
import cats.effect.IO
import jsonrpclib.fs2.*

import io.scalajs.nodejs.fs.*

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import java.net.URI
import java.nio.file.Paths

def server(implicit ec: ExecutionContext): LSPBuilder[scala.concurrent.Future] =
  val state = State.create()
  scribe.Logger.root
    .clearHandlers()
    .withHandler(writer = scribe.writer.SystemErrWriter)
    .replace()

  LSPBuilder
    .create[Future]
    .handleRequest(initialize) { (in, back) =>
      back.notification(
        window.showMessage,
        ShowMessageParams(
          MessageType.Info,
          "Welcome to Tree Sitter grammar LSP"
        )
      )

      Future {
        InitializeResult(
          ServerCapabilities(
            hoverProvider = Opt(true),
            definitionProvider = Opt(true),
            documentSymbolProvider = Opt(true),
            textDocumentSync = Opt(
              TextDocumentSyncOptions(
                openClose = Opt(true),
                save = Opt(true)
              )
            )
          ),
          Opt(
            InitializeResult
              .ServerInfo(
                name = "grammarsy (running on Future)",
                version = Opt("0.0.1")
              )
          )
        )
      }
    }
    .handleNotification(textDocument.didOpen) { (in, _) =>
      val path = in.textDocument.uri.value.drop("file://".length)
      
      val p = Paths.get(in.textDocument.uri.value)
      scribe.info(s"Opened $p")
      Fs.readFileFuture(path, "utf8").map { case str: String =>
        state.updateGrammar(str, in.textDocument.uri)
      }
    }
    .handleNotification(textDocument.didSave) { (in, _) =>
      val path = in.textDocument.uri.value.drop("file://".length)
      Fs.readFileFuture(path, "utf8").map { case str: String =>
        state.updateGrammar(str, in.textDocument.uri)
      }
    }
    .handleRequest(textDocument.documentSymbol) { (in, back) =>
      back.notification(
        window.showMessage,
        ShowMessageParams(
          MessageType.Info,
          "Hello from Langoustine running with Futures!"
        )
      )

      Future {
        Opt {
          state.rules.toOption.toVector.flatten.map {
            case (ruleName, location) =>
              SymbolInformation(
                location = location,
                name = ruleName,
                kind = SymbolKind.Field
              )
          }
        }
      }
    }
    .handleRequest(textDocument.definition) { (in, back) =>
      Future {
        val loc = state.ruleDefinition(in.position).toOption.flatten

        loc match
          case None                => Opt.empty
          case Some((ruleName, v)) => Opt(Definition(v))
      }
    }
    .handleRequest(textDocument.hover) { (in, back) =>
      Future {
        val loc = state.ruleHover(in.position).toOption.flatten

        loc match
          case None => Opt.empty
          case Some((ruleName, contents)) =>
            Opt {
              Hover(contents =
                Vector(
                  MarkedString(s"Reduction `$ruleName`"),
                  MarkedString(MarkedString.S0("javascript", contents))
                )
              )
            }
        end match
      }
    }
end server
