# Lucky Journey

一路顺风鸭（Lucky Journey）是一个基于 Spring Boot 的短视频社区后端项目，包含用户、视频、评论、关注、收藏、后台管理、搜索等核心能力。

## 项目结构

```text
.
├── docs/sql/lucky-journey.sql          # 数据库初始化脚本
├── luckyjourney                        # Spring Boot 后端项目
│   ├── pom.xml
│   └── src
│       ├── main/java/org/luckyjourney
│       │   ├── controller              # 前台与后台接口
│       │   ├── service                 # 业务服务
│       │   ├── mapper                  # MyBatis-Plus 数据访问层
│       │   ├── entity                  # 实体与响应对象
│       │   └── config/util/exception   # 配置与工具
│       └── main/resources
│           ├── application.yml         # 运行配置
│           └── static                  # 静态资源
└── front-end                           # 前端目录（当前仓库中为空）
```

## 技术栈

- Java 8
- Spring Boot 2.7.5
- MyBatis-Plus
- MySQL
- Redis
- RabbitMQ
- Elasticsearch（可开关）
- 七牛云对象存储

## 运行前准备

1. **JDK 8**、**Maven 3.6+**（或使用项目自带 `mvnw`）。
2. 准备基础依赖服务：
   - MySQL（默认库名：`lucky-journey`）
   - Redis
   - RabbitMQ
   - Elasticsearch（可选，默认开启）
3. 初始化数据库：
   - 执行 `docs/sql/lucky-journey.sql`

## 配置说明

后端配置文件位于：

- `/home/runner/work/lucky_journey/lucky_journey/luckyjourney/src/main/resources/application.yml`

常见配置项（支持环境变量覆盖）：

- 数据库：`DATASOURCE_HOST`、`DATASOURCE_NAME`、`DATASOURCE_USER`、`DATASOURCE_PASSWORD`
- Redis：`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`
- RabbitMQ：`RABBITMQ_HOST`、`RABBITMQ_PORT`、`RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD`
- Elasticsearch：`ELASTICSEARCH_ENABLED`、`ELASTICSEARCH_URIS` 等
- 服务端口：`SERVER_PORT`（默认 `8882`）

> 注意：邮件与七牛配置默认为空，需要按实际环境填写。

## 启动方式

在后端目录执行：

```bash
cd /home/runner/work/lucky_journey/lucky_journey/luckyjourney
./mvnw spring-boot:run
```

或先打包再运行：

```bash
cd /home/runner/work/lucky_journey/lucky_journey/luckyjourney
./mvnw clean package
java -jar target/luckyjourney-0.0.1-SNAPSHOT.jar
```

## 接口路径示例

项目包含两类主要接口：

- 前台业务接口：`/luckyjourney/**`
  - 例如：登录、首页推荐、视频、评论、用户中心等
- 后台管理接口：`/admin/**`、`/authorize/**`
  - 例如：用户管理、视频审核、分类管理、权限角色管理、系统配置

## 开发说明

- 主启动类：`org.luckyjourney.LuckyJourneyApplication`
- Mapper 扫描路径：`org.luckyjourney.mapper`
- 项目开启了异步、事务、定时任务支持
- `front-end` 目录目前为空；当前仓库主要为后端实现

## 常见问题

1. **启动报数据库连接失败**
   - 检查 MySQL 是否启动、库名是否正确、环境变量是否覆盖成功。
2. **ES 相关报错**
   - 本地未部署 Elasticsearch 时，可设置 `ELASTICSEARCH_ENABLED=false`。
3. **上传/邮件功能不可用**
   - 检查七牛与邮件账号配置是否已正确填写。

