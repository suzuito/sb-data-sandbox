package com.example.sbdatasandbox

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

// JDBCのObject Mapping Fundamentals
// https://docs.spring.io/spring-data/jdbc/docs/current/reference/html/#mapping.fundamentals
// エンティティをデータベースのレコードへマッピングするための定義
@Table("access_logs")
data class AccessLog(
    // idがNULLのままsaveメソッドが適用された場合、データベースが自動採番する。
    @Id val id: Long? = null,
    var message: String,
    // Spring Boot Data JDBCでは、DDL中で定義されているデフォルト値を使用することは非推奨。
    // https://stackoverflow.com/questions/64429741/how-do-you-insert-with-default-value-in-spring-data-jdbc
    // アプリケーションがデフォルト値を入れるべきらしい。
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)

// 以下のモデルは、データベースによる自動採番を禁止した例
@Table("users")
data class User(
    // idをNULL禁止にする。するとデータベースによる自動採番がされない。
    @Column("id")
    @Id val userId: String,
    val name: String,
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    val updatedAt: OffsetDateTime? = null,
    val deletedAt: OffsetDateTime? = null,

    // idがNULLでない場合、saveメソッドにUserインスタンスを私て実行した時の挙動を制御するための変数。
    // データベースへ保存する必要のないフィールドにはTransientアノテーションをつけること。
    @Transient
    var forceInsertOnSave: Boolean? = false,
) : Persistable<String> {
    // Transientなフィールドがある場合、
    // findBy等のメソッドを用いてデータベースからデータを引き当てる際、
    // forceInsertOnSaveフィールドが存在しない！エラーとなる。
    // Kotlinのクラスには存在しているメンバーが、カラムには存在していないからである。
    // これを回避するために、PersistenceCreatorアノテーションをつけたコンストラクタを作ること（面倒臭いなぁ）
    @PersistenceCreator
    constructor(
        userId: String,
        name: String,
    ) : this(userId = userId, name = name, forceInsertOnSave = false) {
    }

    // UserインスタンスのidをNULLにすることはできない。
    // 従って、Userインスタンスをsaveメソッドに渡し実行すると、常にUpdate文が実行されてしまう。
    // この挙動を変えるためにPersistableを実装する。
    // saveメソッドの挙動をPersistableのisNewメソッドの返り値により制御できる。
    override fun isNew(): Boolean {
        // このメソッドがtrueを返したらINSERT文が実行される。
        return forceInsertOnSave ?: false
    }

    override fun getId(): String {
        return userId
    }
}

@Table("articles")
data class Article(
    @Column("id")
    @Id
    val articleId: String,
    val head: String,
    val description: String,
    val publishedAt: OffsetDateTime? = null,
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    val updatedAt: OffsetDateTime? = null,
    val deletedAt: OffsetDateTime? = null,
    val authorId: String,

    @Transient
    val forceInsertOnSave: Boolean? = false
) : Persistable<String> {
    @PersistenceCreator
    constructor(
        articleId: String,
        authorId: String,
        head: String,
        description: String,
    ) : this(
        articleId = articleId,
        description = description,
        authorId = authorId,
        head = head,
        forceInsertOnSave = false,
    )

    override fun isNew(): Boolean {
        // このメソッドがtrueを返したらINSERT文が実行される。
        return forceInsertOnSave ?: false
    }

    override fun getId(): String {
        return articleId
    }
}


// Spring Dataにおいて、Repositoryインターフェース（を継承したインターフェース）が、
// 実際にデータベースとのやりとりをする。
// Repositoryインターフェースができるのは読み込みだけ。
// CrudRepositoryインターフェースは書き込みもできる。
interface TableUser : CrudRepository<User, String>
interface TableAccessLog : CrudRepository<AccessLog, Long>
interface TableArticle : CrudRepository<Article, String>

@Service
class RepositoryBlog(
    val tableUser: TableUser,
    val tableAccessLog: TableAccessLog,
    val tableArticle: TableArticle,
) {
    fun basicExamples() {
        // データ挿入
        // saveメソッドは、AccessLogのidがnullである場合INSERT文を実行する。
        // idはPostgresにより自動採番される。
        val savedAccessLog = tableAccessLog.save(AccessLog(message = "hoge"))
        println(savedAccessLog)
        // データ更新
        // saveメソッドは、AccessLogのidがnullでない場合UPDATE文を実行する。
        savedAccessLog.message = "fuga"
        val savedAccessLog2 = tableAccessLog.save(savedAccessLog)
        println(savedAccessLog2)

        // データ挿入（id自動採番したくない場合）
        val u = User(userId = "u1", name = "kenshiro", forceInsertOnSave = true)
        val savedUser = tableUser.save(u)
        println(savedUser)

        // 外部キー制約のあるテーブルへのレコード挿入
        val a = Article(
            articleId = "a1",
            head = "head1",
            description = "desc1",
            authorId = u.userId,
            forceInsertOnSave = true,
        )
        val savedArticle = tableArticle.save(a)
        println(savedArticle)
        // どうやら、外部キーをよしなにJoinしてくれるような仕組みはSpring Boot Data JDBCにはないらしい。
        val foundArticle = tableArticle.findById("a1")
        println(foundArticle)
    }

    @Transactional
    fun transactionExample1() {
        // Transaction
        // CrudRepositoryはデフォルトでTransactionが有効になっている。（saveメソッドを実行する際、Begin, Commitが実行されている）
        // https://docs.spring.io/spring-data/jdbc/docs/current/reference/html/#jdbc.transactions
        // しかしながら、
        // Transactionalアノテーションがつけられたメソッドのなかでsaveを実行した場合、
        // メソッドの最初と最後にBegin,Commitされる。
        // これは一体どういう仕組みなのか・・・。
        // 公式ドキュメントによると
        // > Another way to alter transactional behavior is by using a facade or service implementation that typically covers more than one repository. Its purpose is to define transactional boundaries for non-CRUD operations. The following example shows how to create such a facade:
        // とある。
        // transactionExample1実行直後にBEGINクエリが実行される
        tableUser.save(User(userId = "u1", name = "n1", forceInsertOnSave = true))
        // 上のsaveメソッドが終わってもまだCOMMITされない。
        tableArticle.save(
            Article(
                articleId = "a1",
                authorId = "u1",
                head = "head1",
                description = "desc1",
                forceInsertOnSave = true
            )
        )
        tableArticle.save(
            Article(
                articleId = "a2",
                authorId = "u1",
                head = "head2",
                description = "desc2",
                forceInsertOnSave = true
            )
        )
        // ここでCOMMITされる。
    }

    @Transactional
    fun transactionExample2() {
        tableUser.save(User(userId = "u1", name = "n1", forceInsertOnSave = true))
        // 上のsaveメソッドが終わってもまだCOMMITされない。
        tableArticle.save(
            Article(
                articleId = "a1",
                authorId = "u1",
                head = "head1",
                description = "desc1",
                forceInsertOnSave = true
            )
        )
        if (true) {
            return // ここでCOMMITされる。
        }
        tableArticle.save(
            Article(
                articleId = "a2",
                authorId = "u1",
                head = "head2",
                description = "desc2",
                forceInsertOnSave = true
            )
        )
    }

    @Transactional
    fun transactionExample3() {
        tableUser.save(User(userId = "u1", name = "n1", forceInsertOnSave = true))
        // 上のsaveメソッドが終わってもまだCOMMITされない。
        tableArticle.save(
            Article(
                articleId = "a1",
                authorId = "u1",
                head = "head1",
                description = "desc1",
                forceInsertOnSave = true
            )
        )
        throw Error("Dummy error") // ここでRollbackされる
        tableArticle.save(
            Article(
                articleId = "a2",
                authorId = "u1",
                head = "head2",
                description = "desc2",
                forceInsertOnSave = true
            )
        )
    }

    fun clean() {
        tableArticle.deleteAll()
        tableUser.deleteAll()
        tableAccessLog.deleteAll()
    }
}

@SpringBootApplication
// TableArticleインターフェースをJDBC postgresレポジトリ実装することを指定
// これをcom.example.sbdatasandboxパッケージ配下に制限
@EnableJdbcRepositories("com.example.sbdatasandbox")
class SbDataSandboxApplication(
    val repositoryBlog: RepositoryBlog,
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        try {
            // 基本
            // repositoryBlog.basicExamples()
            // トランザクションまわり
            // repositoryBlog.transactionExample1()
            // repositoryBlog.transactionExample2()
            repositoryBlog.transactionExample3()
        } finally {
            repositoryBlog.clean()
        }
    }
}

fun main(args: Array<String>) {
    runApplication<SbDataSandboxApplication>(*args)
}
