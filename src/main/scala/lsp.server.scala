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
import scribe.output.format.ANSIOutputFormat

enum Mode:
  case Open, Save

enum GrammarFile:
  case Spec
  case CorpusFile(nm: String)
  case Unknown(relative: String)

def server(implicit ec: ExecutionContext): LSPBuilder[scala.concurrent.Future] =
  val state = State.create()
  val logic = ServerLogic(state)
  import logic.*

  scribe.Logger.root
    .clearHandlers()
    .withHandler(writer = scribe.writer.SystemErrWriter, outputFormat = ANSIOutputFormat)
    .replace()

  val encoder = SemanticTokensEncoder(
    tokenTypes = Vector(
      SemanticTokenTypes.`type`,
      SemanticTokenTypes.namespace
    ),
    modifiers = Vector.empty
  )

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
                ),
                semanticTokensProvider = Opt(
                  SemanticTokensOptions(
                    legend = encoder.legend,
                    full = Opt(true)
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
    .handleRequest(textDocument.semanticTokens.full) { (in, back) =>

      import corpus.*

      detectFile(in.textDocument.uri.path) match
        case GrammarFile.CorpusFile(_) =>
          state.getCorpus.items.get(in.textDocument.uri.path) match
            case None => Future(Opt.empty)
            case Some(tc) =>
              val tokens = tc.cases.toVector
                .flatMap(indexSemanticTokens)
                .tap(s => scribe.info(s.toString))

              encoder
                .encode(tokens)
                .fold(_ => Future(Opt.empty), t => Future.successful(Opt(t)))

        case _ => Future(Opt.empty)
      end match

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
