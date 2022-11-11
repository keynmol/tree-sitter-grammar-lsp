enablePlugins(ScalablyTypedConverterExternalNpmPlugin, ScalaJSPlugin)

import scala.sys.process.Process

scalaVersion := "3.2.1"

externalNpm := {
  baseDirectory.value
}

Global / onChangedBuildSource := ReloadOnSourceChanges

scalaJSLinkerConfig ~= { conf =>
  conf.withModuleKind(ModuleKind.CommonJSModule)
}

name := "grammar-js-lsp"

scalaJSUseMainModuleInitializer := true

lazy val buildRelease = taskKey[Unit]("")
buildRelease := {
  val base = (ThisBuild / baseDirectory).value
  val loc  = (Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value
  val bin  = base / "bin"

  IO.createDirectory(bin)

  val r = (Compile / fullLinkJS).value

  IO.copyFile(loc / "main.js", bin / "grammar-js-lsp.js")
}

lazy val buildDev = taskKey[Unit]("")
buildDev := {
  val base = (ThisBuild / baseDirectory).value
  val loc  = (Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
  val bin  = base / "bin"

  IO.createDirectory(bin)

  val r = (Compile / fastLinkJS).value

  IO.copyFile(loc / "main.js", bin / "grammar-js-lsp-dev.js")
}

stMinimize      := Selection.AllExcept("acorn")
stUseScalaJsDom := false
stStdlib        := List("es6")
libraryDependencies += "net.exoego" %%% "scala-js-nodejs-v16" % "0.14.0" cross CrossVersion.for3Use2_13

libraryDependencies += "tech.neander" %%% "langoustine-app" % "0.0.17"
