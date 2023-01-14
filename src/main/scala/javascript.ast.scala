package treesitter.lsp

import scalajs.js
import scala.reflect.TypeTest
import typings.acorn.mod.Node

opaque type Program = Node
object Program extends TypeSafety[Program]("Program"):
  extension (n: Program) def body = dispatch[js.Array[Node]](n, "body")

opaque type Identifier = Node
object Identifier extends TypeSafety[Identifier]("Identifier"):
  extension (n: Identifier) def name = dispatch[String](n, "name")

opaque type ReturnStatement = Node
object ReturnStatement extends TypeSafety[ReturnStatement]("ReturnStatement"):
  extension (n: ReturnStatement) def argument = dispatch[Node](n, "argument")

opaque type ExpressionStatement = Node
object ExpressionStatement
    extends TypeSafety[ExpressionStatement]("ExpressionStatement"):
  extension (n: ExpressionStatement)
    def expression =
      dispatch[Node](n, "expression")

opaque type AssignmentExpression = Node
object AssignmentExpression
    extends TypeSafety[AssignmentExpression]("AssignmentExpression"):
  extension (n: AssignmentExpression)
    def left =
      dispatch[Node](n, "left")

    def right =
      dispatch[Node](n, "right")

opaque type MemberExpression = Node
object MemberExpression
    extends TypeSafety[MemberExpression]("MemberExpression"):
  extension (n: MemberExpression)
    def obj =
      dispatch[Node](n, "object")

    def property =
      dispatch[Node](n, "property")

opaque type ArrayExpression = Node
object ArrayExpression extends TypeSafety[ArrayExpression]("ArrayExpression"):
  extension (n: ArrayExpression)
    def elements =
      dispatchArr[Node](n, "elements")

opaque type UnaryExpression = Node
object UnaryExpression extends TypeSafety[UnaryExpression]("UnaryExpression"):
  extension (n: UnaryExpression)
    def argument =
      dispatch[Node](n, "argument")

opaque type ArrowFunctionExpression = Node
object ArrowFunctionExpression
    extends TypeSafety[ArrowFunctionExpression]("ArrowFunctionExpression"):
  extension (n: ArrowFunctionExpression)
    def body =
      dispatch[Node](n, "body")

    def params =
      dispatchArr[Node](n, "params")

opaque type CallExpression = Node
object CallExpression extends TypeSafety[CallExpression]("CallExpression"):
  extension (n: CallExpression)
    def callee    = dispatch[Node](n, "callee")
    def arguments = dispatchArr[Node](n, "arguments")

opaque type Literal = Node
object Literal extends TypeSafety[Literal]("Literal"):
  extension (n: Literal) def raw = dispatch[String](n, "name")

opaque type FunctionDeclaration = Node
object FunctionDeclaration
    extends TypeSafety[FunctionDeclaration]("FunctionDeclaration"):
  extension (fd: FunctionDeclaration)
    def params = dispatch[js.Array[Node]](fd, "params")
    def body   = dispatch[Node](fd, "body")
    def id     = dispatch[Node](fd, "id")

opaque type VariableDeclaration = Node
object VariableDeclaration
    extends TypeSafety[VariableDeclaration]("VariableDeclaration"):
  extension (n: VariableDeclaration)
    def declarations = dispatch[js.Array[Node]](n, "declarations")
end VariableDeclaration

opaque type BlockStatement = Node
object BlockStatement extends TypeSafety[BlockStatement]("BlockStatement"):
  extension (n: BlockStatement) def body = dispatch[js.Array[Node]](n, "body")
end BlockStatement

opaque type VariableDeclarator = Node
object VariableDeclarator
    extends TypeSafety[VariableDeclarator]("VariableDeclarator"):
  extension (n: VariableDeclarator)
    def init = dispatch[Node](n, "init")
    def id   = dispatch[Node](n, "id")
end VariableDeclarator

opaque type Property = Node
object Property extends TypeSafety[Property]("Property"):
  extension (n: Property)
    def key   = dispatch[Node](n, "key")
    def value = dispatch[Node](n, "value")
end Property

opaque type ObjectExpression = Node
object ObjectExpression
    extends TypeSafety[ObjectExpression]("ObjectExpression"):
  extension (n: ObjectExpression)
    def properties = dispatchArr[Node](n, "properties")
end ObjectExpression

extension (n: Node)
  def as[T](t: TypeSafety[T]): Option[T] =
    t.test.unapply(n)

sealed abstract class TypeSafety[T](name: String)(using e: T =:= Node):
  inline def apply(n: Node): T         = n.asInstanceOf[T]
  inline def asNode(inline t: T): Node = t.asInstanceOf[Node]

  extension (f: T)
    def typeName   = e.apply(f).`type`
    def node       = asNode(f)
    def start: Int = asNode(f).start.toInt
    def end: Int   = asNode(f).end.toInt

  inline protected def dispatch[A](pg: T, n: String) =
    pg.asInstanceOf[js.Dynamic]
      .selectDynamic(n)
      .asInstanceOf[A]

  inline protected def dispatchArr[A](pg: T, n: String) =
    dispatch[js.Array[A]](pg, n)

  import compiletime.constValue
  inline given test: TypeTest[Node, T] with
    def unapply(n: Node): Option[n.type & T] =
      if n.`type` == name then Option(apply(n).asInstanceOf[n.type & T])
      else None
end TypeSafety
