package grammarsy

import cats.instances.future.*

import scala.concurrent.ExecutionContext.Implicits.global
import cats.effect.IO
import cats.effect.IOApp

import jsonrpclib.fs2.{*, given}

object GrammarJSServer extends IOApp.Simple:
  def run =
    FS2Channel[IO](2048, None)
      .evalTap(server.bind)
      .flatMap(channel =>
        fs2.Stream
          .eval(IO.never) // running the server forever
          .concurrently(
            fs2.io
              .stdin[IO]
              .through(lsp.decodePayloads[IO])
              .through(channel.input)
          )
          .concurrently(
            channel.output
              .through(lsp.encodePayloads[IO])
              .through(fs2.io.stdout[IO])
          )
      )
      .compile
      .drain
      .guarantee(IO.consoleForIO.errorln("Terminating server"))
  end run
end GrammarJSServer
