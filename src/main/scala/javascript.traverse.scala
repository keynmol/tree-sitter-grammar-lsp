package treesitter.lsp

import io.scalajs.nodejs.console_module.Console as console
import typings.acorn.mod.Node

def traverse(n: Node, f: Node => Unit): Unit =

  inline def handle(n: Node) =
    f(n)
    go(n)

  def go(n: Node): Unit =
    n match
      case vd: VariableDeclaration =>
        vd.declarations.foreach(handle(_))
      case vd: VariableDeclarator =>
        handle(vd.id)
        handle(vd.init)
      case vd: ObjectExpression =>
        vd.properties.foreach(handle(_))
      case vd: FunctionDeclaration =>
        vd.params.foreach(handle(_))
        handle(vd.body)
      case pg: Program =>
        pg.body.foreach(handle(_))
      case bs: BlockStatement =>
        bs.body.foreach(handle(_))
      case id: Identifier =>
        f(id.node)
      case id: Literal =>
        f(id.node)
      case id: Property =>
        handle(id.key)
        handle(id.value)
      case rs: ReturnStatement =>
        handle(rs.argument)
      case rs: ExpressionStatement =>
        handle(rs.expression)
      case rs: AssignmentExpression =>
        handle(rs.left)
        handle(rs.right)
      case rs: ArrowFunctionExpression =>
        rs.params.foreach(handle(_))
        handle(rs.body)
      case rs: CallExpression =>
        handle(rs.callee)
        rs.arguments.foreach(handle(_))
      case rs: MemberExpression =>
        handle(rs.obj)
        handle(rs.property)
      case rs: ArrayExpression =>
        rs.elements.foreach(handle(_))
      case rs: UnaryExpression =>
        handle(rs.argument)
      case other =>
        console.log(other)

  f(n)
  go(n)
end traverse
