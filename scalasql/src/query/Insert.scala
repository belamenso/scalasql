package scalasql.query

import scalasql.dialects.Dialect
import scalasql.{Column, Queryable, query}

/**
 * A SQL `INSERT` query
 */
trait Insert[Q, R] extends WithExpr[Q] with scalasql.generated.Insert[Q, R] {
  def table: TableRef
  def qr: Queryable[Q, R]
  def select[C, R2](columns: Q => C, select: Select[C, R2]): InsertSelect[Q, C, R, R2]
  def values(f: (Q => Column.Assignment[_])*): InsertValues[Q, R]

  def batched[T1](f1: Q => Column.ColumnExpr[T1])(items: Expr[T1]*): InsertValues[Q, R]

}

object Insert {
  class Impl[Q, R](val expr: Q, val table: TableRef)
                  (implicit val qr: Queryable[Q, R], dialect: Dialect)
      extends Insert[Q, R]
      with scalasql.generated.InsertImpl[Q, R] {

    def newInsertSelect[Q, C, R, R2](
        insert: Insert[Q, R],
        columns: C,
        select: Select[C, R2]
    ): InsertSelect[Q, C, R, R2] = { new InsertSelect.Impl(insert, columns, select) }

    def newInsertValues[Q, R](
        insert: Insert[Q, R],
        columns: Seq[Column.ColumnExpr[_]],
        valuesLists: Seq[Seq[Expr[_]]]
    )(implicit qr: Queryable[Q, R]) = { new InsertValues.Impl(insert, columns, valuesLists) }

    def select[C, R2](columns: Q => C, select: Select[C, R2]): InsertSelect[Q, C, R, R2] = {
      newInsertSelect(this, columns(expr), select)
    }

    def values(f: (Q => Column.Assignment[_])*): InsertValues[Q, R] = {
      val kvs = f.map(_(expr))
      newInsertValues(this, columns = kvs.map(_.column), valuesLists = Seq(kvs.map(_.value)))
    }

    def batched[T1](f1: Q => Column.ColumnExpr[T1])(items: Expr[T1]*): InsertValues[Q, R] = {
      newInsertValues(this, columns = Seq(f1(expr)), valuesLists = items.map(Seq(_)))
    }
  }
}
