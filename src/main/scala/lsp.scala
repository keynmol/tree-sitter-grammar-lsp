package grammarsy

import upickle.default.*
import scala.util.*
import langoustine.lsp.*
import requests.*
import notifications as nt
import json.*
import structures.*
import aliases.*
import notifications.LSPNotification
import io.scalajs.nodejs.fs.*
import io.scalajs.nodejs.console_module.Console as console
import scala.concurrent.Future

import cats.instances.future.*
import cats.implicits.*

import scala.concurrent.ExecutionContext.Implicits.global
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.std.Queue

object GrammarJSServer extends IOApp.Simple:
  def run =
    val logger = scribe
      .Logger("LSP")
      .orphan()
      .replace()

    logger.info(s"Starting")

    val state = State.create()

    import jsonrpclib.fs2interop.*
    import jsonrpclib.*

    def server(sink: Communicate[IO]) =
      ImmutableLSPBuilder
        .create[IO]
        .handleRequest(initialize) { (in, back) =>
          IO {
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
                  .ServerInfo(name = "grammarsy", version = Opt("0.0.1"))
              )
            )
          }
        }
        .handleNotification(nt.textDocument.didOpen) { in =>
          val path = in.textDocument.uri.value.drop("file://".length)
          IO.fromFuture {
            IO {
              Fs.readFileFuture(path, "utf8").map { case str: String =>
                state.index(str, in.textDocument.uri)
              }
            }
          }
        }
        .handleNotification(nt.textDocument.didSave) { in =>
          val path = in.textDocument.uri.value.drop("file://".length)
          IO.fromFuture {
            IO {
              Fs.readFileFuture(path, "utf8").map { case str: String =>
                state.index(str, in.textDocument.uri)
              }
            }
          }
        }
        .handleRequest(textDocument.documentSymbol) { (in, back) =>
          back.notification(
            nt.window.showMessage,
            ShowMessageParams(
              enumerations.MessageType.Error,
              "Hello from langoustine!"
            )
          ) *>
            IO {
              state.rules.toOption.toVector.flatten.map {
                case (ruleName, location) =>
                  SymbolInformation(
                    location = location,
                    name = ruleName,
                    kind = enumerations.SymbolKind.Field
                  )
              }
            }
        }
        .handleRequest(textDocument.definition) { (in, back) =>
          IO {
            val loc = state.ruleDefinition(in.position).toOption.flatten

            loc match
              case None                => Definition(Vector.empty)
              case Some((ruleName, v)) => Definition(v)
          }
        }
        .handleRequest(textDocument.hover) { (in, back) =>
          IO {
            val loc = state.ruleHover(in.position).toOption.flatten

            loc match
              case None => Nullable.NULL
              case Some((ruleName, contents)) =>
                Nullable {
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
        .build(sink)

    FS2Channel
      .lspCompliant[IO](
        byteStream = fs2.io.stdin[IO],
        byteSink = fs2.io.stdout[IO],
        startingEndpoints = Nil
      )
      .flatMap { channel =>
        val e1 :: eRest = server(Communicate.channel(channel))

        fs2.Stream.resource(channel.withEndpoints(e1, eRest*))
      }
      .evalMap(_ => IO.never)
      .compile
      .drain
  end run
end GrammarJSServer
