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

@main def hello =
  val logger = scribe
    .Logger("LSP")
    .orphan()
    .replace()

  logger.info(s"Starting")

  var state = Option.empty[Grammar]

  val server = ImmutableLSPBuilder
    .create[Future](logger)
    .handleRequest(initialize) { (in, req) =>
      Future.successful {
        InitializeResult(
          ServerCapabilities(
            hoverProvider = Opt(true),
            definitionProvider = Opt(true)
          ),
          Opt(
            InitializeResult
              .ServerInfo(name = "grammarsy", version = Opt("0.0.1"))
          )
        )
      }
    }
    .handleRequest(textDocument.definition) { (in, req) =>
      Future {
        Definition(Vector.empty)
      }
    }
    .handleNotification(nt.textDocument.didOpen) { (in, req) =>
      val path = in.textDocument.uri.value.drop("file://".length)
      Fs.readFileFuture(path, "utf8").map { case str: String =>
        state = Some(indexGrammar(str))
      }
    }
    .handleRequest(textDocument.hover) { (in, req) =>
      import aliases.MarkedString
      Future.successful {
        Nullable {
          Hover(contents = Vector(MarkedString("Hello"), MarkedString("World")))
        }
      }
    }
    .build

  import langoustine.lsp.requests.LSPRequest

  val definitionRequest = upickle.default.read[ujson.Value]("""
{"position":{"character":7,"line":38},"textDocument":{"uri":"file:\/\/\/Users\/velvetbaldmime\/projects\/langoustine\/README.md"}}
  """.trim)

  val openRequest = upickle.default.read[ujson.Value]("""
  {"textDocument":{"uri":"file:\/\/\/Users\/velvetbaldmime\/projects\/tree-sitter-scala\/grammar.js", "languageId": "javascript", "version": 0, "text": "hello"}}
  """.trim)

  def simulate[T <: LSPRequest](req: T, in: ujson.Value) =
    val r = JSONRPC.request(1, req.requestMethod, in)

    new JSONRPC.RequestMessage:
      def method = req.requestMethod
      def id     = 1
      def params = in
  end simulate

  def simulateN[T <: LSPNotification](req: T, in: ujson.Value) =
    new JSONRPC.Notification:
      def method = req.notificationMethod
      def params = in

  for
    open <- server(simulateN(nt.textDocument.didOpen, openRequest))
    definition <- server(simulate(textDocument.definition, definitionRequest))
    _ = console.log(definition)
  do ()

end hello
