enablePlugins(ScalablyTypedConverterExternalNpmPlugin, ScalaJSPlugin)

import scala.sys.process.Process

scalaVersion := "3.1.3"

externalNpm := {
  baseDirectory.value
}

Global / onChangedBuildSource := ReloadOnSourceChanges

scalaJSLinkerConfig ~= { conf =>
  conf.withModuleKind(ModuleKind.CommonJSModule)
}

scalaJSUseMainModuleInitializer := true

stMinimize      := Selection.AllExcept("acorn")
stUseScalaJsDom := false
stStdlib        := List("es6")
libraryDependencies += "net.exoego" %%% "scala-js-nodejs-v16" % "0.14.0" cross CrossVersion.for3Use2_13

libraryDependencies += "tech.neander" %%% "jsonrpclib-fs2" % "0.0.1"
libraryDependencies += "tech.neander" %%% "langoustine-lsp" % "0.0.4"
libraryDependencies += "co.fs2" %%% "fs2-io" % "3.2.11"
