# SpringBoot Dataを勉強するためのレポジトリ

## 開発環境構築方法

データベースをDocker上で作る。

```shell
docker compose up
```

データベースへログインする方法。

```shell
psql --username=sa --dbname=postgres --host=127.0.0.1
# パスワードも"sa"
```

## SpringBoot Dataとは

SpringBoot Dataモジュールは、データ永続化層へのアクセスを提供する。以下の特徴があるっぽい。

データベースとのやりとりを行う部分の詳細実装を隠蔽できる。
Repositoryというインターフェースを介してデータ永続化層を操作することにより実現する。

## とりあえず使ってみた

### Spring Boot Data JDBC

- com.example.sbdatasandbox.SbDataSandboxApplication.kt

所感

- 外部キーによる高機能な連結機能はない。
- シンプル is the best。
- 普通にSQLを書く必要あり。
- ORマッパーではない。
