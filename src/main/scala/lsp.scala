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

@main def hello =
  val logger = scribe
    .Logger("LSP")
    .orphan()
    .replace()

  logger.info(s"Starting")

  var state = Option.empty[Grammar]

  val server = ImmutableLSPBuilder
    .create[Try](logger)
    .handleRequest(initialize) { (in, req) =>

      println(in.workspaceFolders)
      Success {
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
      Success {
        Definition(Vector.empty)
      }
    }
    .handleNotification(nt.textDocument.didOpen) { (in, req) =>
      println(in)
      Success { () }
    }
    .handleRequest(textDocument.hover) { (in, req) =>
      import aliases.MarkedString
      Success {
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
  {"textDocument":{"uri":"file:\/\/\/Users\/velvetbaldmime\/projects\/langoustine\/README.md", "languageId": "javascript", "version": 0, "text": "hello"}}
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

  println(
    server(simulate(textDocument.definition, definitionRequest)).get.get.result
  )
  println(
    server(simulateN(nt.textDocument.didOpen, openRequest)).get
  )

end hello
