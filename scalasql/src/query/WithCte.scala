package scalasql.query

import scalasql.dialects.Dialect
import scalasql.renderer.SqlStr.{Renderable, SqlStringSyntax}
import scalasql.renderer.{Context, ExprsToSql, SqlStr}
import scalasql.utils.FlatJson
import scalasql.{Queryable, TypeMapper}

/**
 * A SQL `WITH` clause
 */
class WithCte[Q, R](
    val lhs: Select[_, _],
    val lhsSubQuery: WithCteRef[_, _],
    val rhs: Select[Q, R],
    val withPrefix: SqlStr = sql"WITH "
)(implicit val qr: Queryable.Row[Q, R], protected val dialect: Dialect)
    extends Select.Proxy[Q, R] {

  override protected def expr = WithExpr.get(Joinable.joinableSelect(rhs))
  private def unprefixed = new WithCte(lhs, lhsSubQuery, rhs, SqlStr.commaSep)

  protected def selectSimpleFrom() = this.subquery

  override def map[Q2, R2](f: Q => Q2)(implicit qr2: Queryable.Row[Q2, R2]): Select[Q2, R2] = {
    new WithCte(lhs, lhsSubQuery, rhs.map(f))
  }

  override def filter(f: Q => Expr[Boolean]): Select[Q, R] = {
    new WithCte(rhs.filter(f), lhsSubQuery, rhs)
  }

  override def sortBy(f: Q => Expr[_]) = new WithCte(lhs, lhsSubQuery, rhs.sortBy(f))

  override def drop(n: Int) = new WithCte(lhs, lhsSubQuery, rhs.drop(n))
  override def take(n: Int) = new WithCte(lhs, lhsSubQuery, rhs.take(n))

  override protected def selectRenderer(prevContext: Context) =
    new WithCte.Renderer(withPrefix, this, prevContext)

  override protected def selectLhsMap(prevContext: Context): Map[Expr.Identity, SqlStr] = {
    Select.selectLhsMap(rhs, prevContext)
  }

  override protected def queryConstruct(args: Queryable.ResultSetIterator): Seq[R] =
    Query.queryConstruct(rhs, args)
}

object WithCte {
  class Proxy[Q, R](
      lhs: WithExpr[Q],
      lhsSubQueryRef: WithCteRef[Q, R],
      val qr: Queryable.Row[Q, R],
      protected val dialect: Dialect
  ) extends Select.Proxy[Q, R] {
//    override def joinableSelect = this
    override def joinableIsTrivial = true
    protected override def joinableSelect = selectSimpleFrom()
    override protected def selectSimpleFrom(): SimpleSelect[Q, R] =
      new SimpleSelect[Q, R](
        expr = WithExpr.get(lhs),
        exprPrefix = None,
        from = Seq(lhsSubQueryRef),
        joins = Nil,
        where = Nil,
        groupBy0 = None
      )(qr, dialect)

    override def selectRenderer(prevContext: Context): Select.Renderer = new Select.Renderer {
      def render(liveExprs: Option[Set[Expr.Identity]]): SqlStr = {
        SqlStr.raw(prevContext.fromNaming(lhsSubQueryRef))
      }
    }

    override protected def renderToSql(ctx: Context): SqlStr = {
      SqlStr.raw(ctx.fromNaming(lhsSubQueryRef))
    }
  }

  class Renderer[Q, R](withPrefix: SqlStr, query: WithCte[Q, R], prevContext: Context)
      extends Select.Renderer {
    def render(liveExprs: Option[Set[Expr.Identity]]) = {
      val walked = query.lhs.qr.asInstanceOf[Queryable[Any, Any]].walk(WithExpr.get(query.lhs))
      val newExprNaming = walked.map { case (tokens, expr) =>
        (
          Expr.exprIdentity(expr),
          SqlStr.raw(
            prevContext.config.tableNameMapper(FlatJson.flatten(tokens, prevContext)),
            Array(Expr.exprIdentity(expr))
          )
        )
      }

      val newContext = Context.compute(prevContext, Seq(query.lhsSubQuery), None)
      val cteName = SqlStr.raw(newContext.fromNaming(query.lhsSubQuery))
      val rhsSql = SqlStr.flatten(
        (query.rhs match {
          case w: WithCte[Q, R] => SqlStr.empty
          case r => sql" "
        }) +
          Select
            .selectRenderer(
              query.rhs match {
                case w: WithCte[Q, R] => w.unprefixed
                case r => r
              },
              newContext.withExprNaming(
                newContext.exprNaming ++
                  newExprNaming.map { case (k, v) => (k, sql"$cteName.$v") }
              )
            )
            .render(liveExprs)
      )
      val rhsReferenced = rhsSql.referencedExprs.toSet
      val lhsSql = Select.selectRenderer(query.lhs, prevContext).render(Some(rhsReferenced))

      val cteColumns = SqlStr.join(
        newExprNaming.collect { case (exprId, name) if rhsReferenced.contains(exprId) => name },
        SqlStr.commaSep
      )

      sql"$withPrefix$cteName ($cteColumns) AS ($lhsSql)$rhsSql"
    }

  }
}
