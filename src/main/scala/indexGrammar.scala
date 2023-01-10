package treesitter.lsp

import typings.acorn.mod.*
import typings.acorn.mod.Options
import typings.acorn.mod.ecmaVersion
import langoustine.lsp.all.*

def processReduction(n: Node): Reductions =
  var red = Reductions.of(None)
  traverse(
    n,
    {
      case m: MemberExpression if m.obj.as(Identifier).exists(_.name == "$") =>
        val mention = m.property.as(Identifier).map { i =>
          Mention(i.name, Pos.of(i.node))
        }
        red = red ++ Reductions.of(mention)
      case _ =>
    }
  )
  red
end processReduction

def findReductions(objectExpr: ObjectExpression): Map[Rule, Reductions] =
  val props = objectExpr.properties.flatMap(_.as(Property))
  val rules = props
    .find(_.key.as(Identifier).exists(_.name == "rules"))
    .flatMap(_.value.as(ObjectExpression))
    .toList
    .flatMap(_.properties.flatMap(_.as(Property)))

  def isGrammarThing(af: ArrowFunctionExpression): Boolean =
    af.params.headOption.flatMap(_.as(Identifier)).exists(_.name == "$")

  rules.map { prop =>
    val ruleName = prop.key.as(Identifier).get.name
    val rule     = Rule(ruleName, Pos.of(prop.node))

    val reductions = prop.value
      .as(ArrowFunctionExpression)
      .filter(isGrammarThing)
      .map(_.body)
      .map(processReduction)
      .getOrElse(Reductions.of(None))

    rule -> reductions
  }.toMap
end findReductions

def indexGrammar(input: String, location: DocumentUri): Grammar =
  import typings.acorn.acornRequire
  acornRequire

  val ast  = typings.acorn.mod.parse(input, Options(ecmaVersion.`2022`))
  var gram = Option.empty[Grammar]

  traverse(
    ast,
    node =>
      node.as(CallExpression).foreach { ce =>
        if ce.callee.as(Identifier).map(_.name) == Some("grammar") then
          ce.arguments.headOption
            .flatMap(_.as(ObjectExpression))
            .foreach { oe =>
              gram =
                Some(Grammar(findReductions(oe), TextIndex.of(input), location))
            }
      }
  )

  gram.get
end indexGrammar
