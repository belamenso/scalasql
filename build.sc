import $file.buildutil.generateDocs
import mill._, scalalib._

val scalaVersions = Seq("2.13.12"/*, "3.3.1"*/)

object scalasql extends Cross[ScalaSql](scalaVersions)
trait ScalaSql extends CrossScalaModule{
  def scalaVersion = crossScalaVersion
  def ivyDeps = Agg(
    ivy"com.lihaoyi::sourcecode:0.3.1",
    ivy"com.lihaoyi::upickle-implicits:3.1.3",

    ivy"org.apache.logging.log4j:log4j-api:2.20.0",
    ivy"org.apache.logging.log4j:log4j-core:2.20.0",
    ivy"org.apache.logging.log4j:log4j-slf4j-impl:2.20.0",
    ivy"com.lihaoyi::pprint:0.8.1",
  ) ++ Option.when(scalaVersion().startsWith("2."))(
    ivy"org.scala-lang:scala-reflect:$scalaVersion"
  )

  def generatedSources: T[Seq[PathRef]] = T{
    def defs(isImpl: Boolean) = {
      for(i <- Range(2, 22)) yield {
        def csep(f: Int => String) = Range.inclusive(1, i).map(f).mkString(", ")
        
        val impl =
          if (!isImpl) ""
          else s"""= newInsertValues(
                 |        this,
                 |        columns = Seq(${csep(j => s"f$j(expr)")}),
                 |        valuesLists = items.map(t => Seq(${csep(j => s"t._$j")}))
                 |      )
                 |
                 |""".stripMargin
        s"""def batched[${csep(j => s"T$j")}](${csep(j => s"f$j: Q => Column.ColumnExpr[T$j]")})(
          |    items: (${csep(j => s"Expr[T$j]")})*
          |)(implicit qr: Queryable[Q, R]): scalasql.query.InsertValues[Q, R] $impl""".stripMargin
      }
    }

    os.write(
      T.dest / "Generated.scala",
      s"""package scalasql.query.generated
        |import scalasql.Column
        |import scalasql.Queryable
        |import scalasql.query.Expr
        |trait Insert[Q, R]{
        |  ${defs(false).mkString("\n")}
        |}
        |trait InsertImpl[Q, R] extends Insert[Q, R]{ this: scalasql.query.Insert[Q, R] =>
        |  def newInsertValues[Q, R](
        |        insert: scalasql.query.Insert[Q, R],
        |        columns: Seq[Column.ColumnExpr[_]],
        |        valuesLists: Seq[Seq[Expr[_]]]
        |    )(implicit qr: Queryable[Q, R]): scalasql.query.InsertValues[Q, R]
        |  ${defs(true).mkString("\n")}
        |}
        |""".stripMargin
    )
    Seq(PathRef(T.dest / "Generated.scala"))
  }

  object test extends ScalaTests {
    def ivyDeps = Agg(
      ivy"com.github.vertical-blank:sql-formatter:2.0.4",
      ivy"com.lihaoyi::mainargs:0.4.0",
      ivy"com.lihaoyi::os-lib:0.9.1",
      ivy"com.lihaoyi::upickle:3.1.3",
      ivy"com.lihaoyi::utest:0.8.2",
      ivy"com.h2database:h2:2.2.224",
      ivy"org.xerial:sqlite-jdbc:3.43.0.0",
      ivy"org.testcontainers:postgresql:1.19.1",
      ivy"org.postgresql:postgresql:42.6.0",
      ivy"org.testcontainers:mysql:1.19.1",
      ivy"mysql:mysql-connector-java:8.0.33",
      ivy"com.zaxxer:HikariCP:5.1.0"
    )

    def testFramework = "scalasql.UtestFramework"
  }
}

val generatedCodeHeader = "[//]: # (GENERATED SOURCES, DO NOT EDIT DIRECTLY)"
def generateTutorial() = T.command {
  generateDocs.generateTutorial(
    os.pwd / "scalasql" / "test" / "src" / "WorldSqlTests.scala",
    os.pwd / "docs" / "tutorial.md"
  )
}
def generateReference() = T.command {
  generateDocs.generateReference(
    os.pwd / "docs" / "reference.md",
    (sources, config) =>
      mill.scalalib.scalafmt.ScalafmtWorkerModule
        .worker()
        .reformat(sources.map(PathRef(_)), PathRef(config))
  )
}
