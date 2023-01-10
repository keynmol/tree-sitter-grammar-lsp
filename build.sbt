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

val BinaryName = "tree-sitter-grammar-lsp"

name := BinaryName

scalaJSUseMainModuleInitializer := true

import sys.process.*

lazy val npmInstall = taskKey[Unit]("")
npmInstall := {
  "npm install".!!
}

lazy val buildRelease = taskKey[Unit]("")
buildRelease := {
  build(
    (ThisBuild / baseDirectory).value,
    (Compile / fullLinkJSOutput).value,
    s"$BinaryName.js"
  )
}

lazy val buildDev = taskKey[Unit]("")
buildDev := {
  build(
    (ThisBuild / baseDirectory).value,
    (Compile / fastLinkJSOutput).value,
    s"$BinaryName-dev.js"
  )
}

def build(base: File, sjs: File, name: String) = {

  val bin = base / "bin"

  IO.createDirectory(bin)
  val jsPath = bin / name

  IO.copyFile(sjs / "main.js", jsPath)

  "npm install".!!
  Process(Seq("npm", "exec", "-c", s"pkg $jsPath --out-path $bin")).!!
}

stMinimize      := Selection.AllExcept("acorn")
stUseScalaJsDom := false
stStdlib        := List("es6")
libraryDependencies += "net.exoego" %%% "scala-js-nodejs-v16" % "0.14.0" cross CrossVersion.for3Use2_13
libraryDependencies += "com.github.j-mie6" %%% "parsley" % "4.0.3"

resolvers ++= Resolver.sonatypeOssRepos("snapshots")

libraryDependencies += "tech.neander" %%% "langoustine-app" % "0.0.19+5-dc569a9e-SNAPSHOT"
