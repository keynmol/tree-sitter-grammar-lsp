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

## Installation

1. Grab a release from the https://github.com/keynmol/tree-sitter-grammar-lsp/releases/ tab
   
   Alternatively, you can build it locally by running `sbt buildDev` - the binaries 
   for all platforms will be put in the `/bin/` folder
2. Configure your editor, here's the instructions for Neovim:

Ensure you have [nvim-lspconfig](https://github.com/neovim/nvim-lspconfig)
installed, and then add this to your config:

```lua
 vim.api.nvim_create_autocmd({ "BufRead", "BufNewFile" }, {
    pattern = { "grammar.js" },
    callback = function() vim.cmd("setfiletype tree-sitter-grammar") end
  })

  vim.api.nvim_create_autocmd({ "BufReadPost" }, {
    pattern = { "grammar.js" },
    command = "set syntax=javascript"
  })

  local configs = require("lspconfig.configs")
  local util = require("lspconfig.util")
  configs.tree_sitter_grammar_lsp = {
    default_config = {
      cmd = { "tree-sitter-grammar-lsp-<your-os>" }, -- update to match your local installation location
      filetypes = { "tree-sitter-grammar" },
      root_dir = util.path.dirname
    }
  }
```

Then you're able to call setup just like any other server you may be using from
`nvim-lspconfig`.

```lua
local configs = require("lspconfig")
configs.tree_sitter_grammar_lsp.setup({})
```

## Contributing

My recommendation is to point your LSP config to a locally checked out copy of 
this repository, specifically at `<root>/bin/tree-sitter-grammar-lsp-dev-<your-platform>`.

To produce that binary, you can run `sbt buildDev` - which will produce non-optimised
JS version, and should be quicker to build iteratively.

If you want to produce a binary with all the optimisations, run `sbt buildRelease`.

Happy hacking!
