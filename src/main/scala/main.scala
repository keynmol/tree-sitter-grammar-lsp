package grammarsy

import cats.instances.future.*
import cats.implicits.*

import scala.concurrent.ExecutionContext.Implicits.global
import cats.effect.IO
import cats.effect.IOApp

import jsonrpclib.fs2.*

object GrammarJSServer extends IOApp.Simple:
  def run =
    FS2Channel
      .lspCompliant[IO](
        byteStream = fs2.io.stdin[IO],
        byteSink = fs2.io.stdout[IO],
        startingEndpoints = Nil
      )
      .evalMap(server.bind)
      .evalMap(_ => IO.never)
      .compile
      .drain
  end run
end GrammarJSServer
