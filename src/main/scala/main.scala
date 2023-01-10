package treesitter.lsp

import scala.concurrent.ExecutionContext.Implicits.global
import langoustine.lsp.app.LangoustineApp
import scala.concurrent.Future

object GrammarJSServer extends LangoustineApp.FromFuture.Simple:
  def server = Future { treesitter.lsp.server }
