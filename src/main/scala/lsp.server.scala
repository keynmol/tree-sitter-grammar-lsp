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

enum Mode:
  case Open, Save

enum GrammarFile:
  case Spec
  case CorpusFile(nm: String)
  case Unknown(relative: String)

class ServerLogic(state: State)(using ExecutionContext):
  def detectFile(path: DocumentPath): GrammarFile =
    state.getRoot
      .map { rootPath =>
        val relative = JSPath.relative(rootPath.value, path.value)
        // scribe.info(s"Root: $rootPath, path: $path, ext: ${path.ext}, relative: ${JSPath.basename(relative)}")
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
end ServerLogic

def server(implicit ec: ExecutionContext): LSPBuilder[scala.concurrent.Future] =
  val state = State.create()
  val logic = ServerLogic(state)
  import logic.*

  scribe.Logger.root
    .clearHandlers()
    .withHandler(writer = scribe.writer.SystemErrWriter)
    .replace()

  LSPBuilder
    .create[Future]
    .handleRequest(initialize) { (in, back) =>
      in.rootUri.toOption.foreach { rootUri =>
        state.setRoot(rootUri.path)
      }

      back
        .notification(
          window.showMessage,
          ShowMessageParams(
            MessageType.Info,
            "Welcome to Tree Sitter grammar LSP"
          )
        )
        .flatMap { _ =>
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
                    name = "Tree Sitter grammar LSP",
                    version = Opt("0.0.1")
                  )
              )
            )
          }
        }
    }
    .handleNotification(textDocument.didOpen) { (in, _) =>
      handleFile(in.textDocument.uri.path, Mode.Open)
    }
    .handleNotification(textDocument.didSave) { (in, _) =>
      handleFile(in.textDocument.uri.path, Mode.Save)
    }
    .handleRequest(textDocument.documentSymbol) { (in, back) =>
      Future {
        detectFile(in.textDocument.uri.path) match
          case GrammarFile.Spec =>
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
          case GrammarFile.CorpusFile(nm) =>
            state.getCorpus.items.get(in.textDocument.uri.path) match
              case None => Opt.empty
              case Some(tc) =>
                Opt {
                  tc.cases.toVector.map { cs =>
                    SymbolInformation(
                      location = Location(
                        in.textDocument.uri,
                        cs.title.span
                      ),
                      name = cs.title.value,
                      kind = SymbolKind.String
                    )
                  }
                }
            end match

          case GrammarFile.Unknown(relative) =>
            Opt.empty

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
