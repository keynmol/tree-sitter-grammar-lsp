package treesitter.lsp

import langoustine.lsp.all.Position

object TestkitTests extends weaver.FunSuite:
  test("slice position + length") {
    val text =
      """
      |hello world
      |11,22
      |3456
      """.stripMargin.trim

    expect.all(
      text.slice(Position(line = 1, character = 0), 2) == "11",
      text.slice(Position(line = 1, character = 3), 2) == "22",
      text.slice(Position(line = 0, character = 0), 11) == "hello world",
      text.slice(Position(line = 0, character = 0), 17) == "hello world\n11,22"
    )
  }
end TestkitTests

extension (s: String)
  def slice(r: langoustine.lsp.structures.Range): String =
    val result = StringBuilder()
    s.linesIterator.zipWithIndex
      .slice(r.start.line.value, r.end.line.value)
      .foreach { (line, idx) =>
        if idx == r.start.line.value then
          result.append(line.drop(r.start.character.value))
        else if idx == r.end.line.value then
          result.append(line.take(r.end.character.value))
        else
          result.append(line)
          if idx > r.start.line.value then result.append(System.lineSeparator())
      }

    result.result
  end slice

  def slice(r: Position, length: Int): String =
    val result = StringBuilder()
    s.linesIterator.zipWithIndex
      .drop(r.line.value)
      .foreach { (line, idx) =>
        val remaining = length - result.size
        if remaining > 0 then 
          val trimmedLine = if idx == r.line.value then line.drop(r.character.value) else line
          result.append(trimmedLine.take(remaining))
          if remaining > trimmedLine.length then result.append(System.lineSeparator())
      }
    result.result
end extension
