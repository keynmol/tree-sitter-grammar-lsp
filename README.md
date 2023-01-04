# Tree Sitter Grammar.js LSP

Specialised LSP to edit one file and one file only - `grammar.js` used to specify Tree sitter grammars.

Made using [Langoustine](https://github.com/neandertech/langoustine) 
and [Jsonrpclib](https://github.com/neandertech/jsonrpclib).

[Scala 3](https://docs.scala-lang.org/scala3/book/introduction.html#), 
[Scala.js](https://www.scala-js.org), 
[ScalablyTyped](https://scalablytyped.org/docs/readme.html),
[Cats-effect 3](https://typelevel.org/cats-effect/),
[FS2](https://fs2.io/#/)

![2022-08-03 15 39 29](https://user-images.githubusercontent.com/1052965/182636739-3f63349b-2336-4afa-8fc9-767b392df25b.gif)

1. Grab a release from https://github.com/keynmol/tree-sitter-grammar-lsp/releases/ tab
2. Configure your editor, here's the instructions for Neovim:

Put this in your `init.lua`:

```lua
local lsp = vim.api.nvim_create_augroup("LSP", { clear = true })

vim.api.nvim_create_autocmd("FileType", {
    group = lsp,
    pattern = "tree-sitter-grammar",
    callback = function()
        local grammarjsLSP = 'FULL_PATH_TO_THE_BINARY'
        local path = vim.fs.find({ "grammar.js" })
        vim.lsp.start({
            name = "tree-sitter-grammar-lsp",
            cmd = { grammarjsLSP },
            root_dir = vim.fs.dirname(path[1])
        })
    end,
})

vim.api.nvim_create_autocmd({ "BufRead", "BufNewFile" }, {
  pattern = { "grammar.js" },
  callback = function() vim.cmd("setfiletype tree-sitter-grammar") end
})

vim.api.nvim_create_autocmd({ "BufReadPost" }, {
  pattern = { "grammar.js" },
  command = "set syntax=javascript"
})
```
