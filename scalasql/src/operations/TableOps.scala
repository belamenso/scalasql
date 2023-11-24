package scalasql.operations

import scalasql.{Column, Id, Table}
import scalasql.query.{Delete, Expr, Insert, Joinable, Select, SimpleSelect, Update}

class TableOps[V[_[_]]](val t: Table[V]) extends Joinable[V[Expr], V[Id]] {

  protected def toFromExpr0 = {
    val ref = Table.tableRef(t)
    (ref, Table.tableMetadata(t).vExpr(ref))
  }

  protected def joinableToFromExpr = {
    val (ref, expr) = toFromExpr0
    (ref, expr.asInstanceOf[V[Expr]])
  }

  protected def joinableSelect: Select[V[Expr], V[Id]] = {
    val (ref, expr) = joinableToFromExpr
    new SimpleSelect(expr, None, Seq(ref), Nil, Nil, None)(
      t.containerQr
    )
  }

  /**
   * Constructs a `SELECT` query
   */
  def select = joinableSelect

  /**
   * Constructs a `UPDATE` query with the given [[filter]] to select the
   * rows you want to delete
   */
  def update(filter: V[Column.ColumnExpr] => Expr[Boolean]): Update[V[Column.ColumnExpr], V[Id]] = {
    val (ref, expr) = toFromExpr0
    new Update.Impl(expr, ref, Nil, Nil, Seq(filter(Table.tableMetadata(t).vExpr(ref))))(
      t.containerQr
    )
  }

  /**
   * Constructs a `INSERT` query
   */
  def insert: Insert[V[Column.ColumnExpr], V[Id]] = {
    val (ref, expr) = toFromExpr0
    new Insert.Impl(expr, ref)(t.containerQr)
  }

  /**
   * Constructs a `DELETE` query with the given [[filter]] to select the
   * rows you want to delete
   */
  def delete(filter: V[Column.ColumnExpr] => Expr[Boolean]): Delete[V[Column.ColumnExpr]] = {
    val (ref, expr) = toFromExpr0
    new Delete.Impl(expr, filter(Table.tableMetadata(t).vExpr(ref)), ref)
  }

  protected def joinableIsTrivial = true
}
