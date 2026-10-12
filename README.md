# Demo — Spring Boot 4 / Java 25 业务架构示例

一个可直接运行的「主流业务后端」参考实现：分层架构、统一响应与错误码、充血领域模型、
JWT 鉴权（角色级 + 数据级）、JPA 审计与乐观锁、Flyway 迁移、虚拟线程、OpenAPI 文档，以及覆盖
单元 / Web / 真实容器三个层次的测试。

> 该工程最初是一份**无法编译的骨架**（7 个空占位类、3 处编译错误、缺失安全与持久化层）。
> 本次已补全为可运行、可测试的完整实现；与原骨架的差异见文末「补齐清单」。

## 技术栈

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Java | 25 (LTS) | records、sealed、pattern matching、`Math.clamp`、虚拟线程 |
| Spring Boot | 4.1.1 | 注意：Boot 4 的自动配置按技术拆分为独立模块（如 `spring-boot-flyway`） |
| Spring Framework | 7.0.9 | MVC + `RestClient` |
| Spring Security | 7.1.1 | OAuth2 Resource Server（JWT）+ `@EnableMethodSecurity` |
| Spring Data JPA | 4.1.1 | Specification、Auditing、乐观锁 |
| Hibernate ORM | 7.4.5 | `ddl-auto=validate`，DDL 交给 Flyway |
| Jackson | 3.1.5 | 包名为 `tools.jackson.*`（注解仍在 `com.fasterxml.jackson.annotation`） |
| PostgreSQL / H2 | 42.7.13 / 2.4 | 生产 PostgreSQL 18；本地/测试 H2（PostgreSQL 兼容模式） |
| SpringDoc OpenAPI | 3.1.0 | Swagger UI + Bearer 鉴权声明 |
| 测试 | JUnit 6 + Mockito 5 + AssertJ | 184 个测试 |

## 快速开始

### 本地（零外部依赖）

```bash
./mvnw spring-boot:run                                        # 插件已把 dev 设为默认 profile
java -jar target/Demo-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

> 打包产物**不配置默认 profile**。未指定 profile 时 Spring Boot 回退到嵌入式 H2（空库）：
> 不创建任何账号、开放注册关闭、密钥为进程内随机值 —— 既不会把开发配置（演示账号、开放注册）
> 带上线，也不会因为漏配而对外暴露任何入口；部署生产必须显式 `--spring.profiles.active=prod`
> 并注入 `DB_USER`/`DB_PASSWORD`/`JWT_SECRET`。

dev 环境使用 H2 内存库（PostgreSQL 兼容模式），启动即执行 Flyway 迁移，并创建演示账号
（仅本地演示用途；生产 profile 不创建任何账号）：

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `admin12345` | ADMIN, USER |
| `demo` | `demo12345` | USER |

dev 下若未注入 `JWT_SECRET`，进程会在启动时生成**随机 HS256 密钥**（不落盘、不进仓库，重启后旧令牌失效），
因此仓库内不存在可用于伪造令牌的固定密钥。

- H2 控制台：<http://localhost:8080/h2-console>（JDBC URL `jdbc:h2:mem:demo`，用户 `sa`，空密码）
- Swagger UI：<http://localhost:8080/swagger-ui.html>
- 健康检查：<http://localhost:8080/actuator/health>

### 生产（PostgreSQL）

```bash
export DB_USER=... DB_PASSWORD=... JWT_SECRET=<至少32字节的随机串>
java -jar target/Demo-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

prod 环境：Flyway 管理表结构、Hibernate 只做 `validate`、不创建演示账号、关闭开放注册与接口文档、
不暴露 H2 控制台。可选变量：`DB_URL`、`DB_POOL_SIZE`、`JWT_ISSUER`、`JWT_AUDIENCE`、`CORS_ALLOWED_ORIGINS`。

本地用真实 PostgreSQL 验证 prod（本项目已按此流程实证过）：

```bash
createuser demo --login --pwprompt && createdb -O demo demo
export DB_USER=demo DB_PASSWORD='<上一步设置的密码>' JWT_SECRET="$(openssl rand -base64 48)"
java -jar target/Demo-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
# 首次启动由 Flyway 建表；重复启动会输出 "Schema \"public\" is up to date"
```

### 测试

```bash
./mvnw test                            # 全量（含真实 Tomcat 的 RANDOM_PORT 冒烟）
./mvnw test -Dtest=UserServiceImplTest  # 单测
```

## 请求链路

```mermaid
flowchart LR
  C[客户端] -->|Bearer JWT| SF[SecurityFilterChain<br/>无状态 / CSRF 关闭]
  SF -->|401/403 统一包体| C
  SF --> CTRL[Controller<br/>@Valid 参数校验<br/>@PreAuthorize 角色鉴权]
  CTRL --> SVC[Service<br/>事务边界 / 数据级鉴权]
  SVC --> CONV[Converter<br/>脱敏 + DTO]
  SVC --> REPO[Repository<br/>Specification / Pageable]
  REPO --> DB[(PostgreSQL / H2)]
  CTRL -.异常.-> GEH[GlobalExceptionHandler<br/>异常 → HTTP 状态 + 业务码]
  GEH --> C
```

分层规则：Controller 只做协议转换与角色级鉴权；Service 持有事务与业务规则；实体负责领域不变量
（金额、状态机）；Repository 不感知业务。

## 目录结构

```
src/main/java/com/example/demo
├── api/            统一响应包体 ApiResponse(sealed) / PageResponse / ErrorCode
├── common/
│   ├── exception/  BusinessException / NotFoundException / GlobalExceptionHandler
│   ├── generator/  OrderNumberGenerator（可注入 Clock）
│   ├── util/       Strings（含手机号/邮箱脱敏）
│   └── validation/ @ValidPhone + 校验器
├── config/         SecurityConfig / JsonConfig / CorsConfig / JpaAuditingConfig
│                   DemoProperties（demo.* 配置）/ OpenApiConfig / DemoDataSeeder
├── controller/     User / Order / Auth / System
├── converter/      UserConverter / OrderConverter
├── enums/          OrderStatus（状态机）/ UserStatus / Role
├── model/          entity（User / Order / OrderItem / BaseEntity）、dto、query
├── repository/     UserRepository / OrderRepository
├── security/       AuthenticatedUser / CurrentUserProvider
└── service/        UserService / OrderService / AuthService（+ impl）
src/main/resources
├── application.yaml / application-dev.yaml / application-prod.yaml
└── db/migration/V1__baseline_schema.sql
```

## API 一览

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/auth/token` | 公开 | 账号密码换取 JWT |
| POST | `/api/auth/register` | 公开* | 开放注册（仅普通用户；*生产默认关闭） |
| GET | `/api/users` | ADMIN | 分页 + 关键字 + 状态筛选，排序字段白名单 |
| POST | `/api/users` | ADMIN | 创建用户（201） |
| GET | `/api/users/{id}` | ADMIN | 用户详情 |
| PATCH | `/api/users/{id}` | ADMIN | 改邮箱/手机号/状态/密码（`null` 表示不改） |
| DELETE | `/api/users/{id}` | ADMIN | 删除用户 |
| GET | `/api/users/me` | 已认证 | 当前用户资料 |
| PATCH | `/api/users/me` | 已认证 | 自助修改资料（不含状态；只提交需要修改的字段） |
| POST | `/api/orders` | 已认证 | 下单（下单人取自 JWT，客户端无法伪造） |
| GET | `/api/orders` | ADMIN | 全量订单（可按订单号/状态/用户筛选） |
| GET | `/api/orders/me` | 已认证 | 我的订单（服务端强制限定本人，ADMIN 调用同样只返回自己） |
| GET | `/api/orders/{id}` | 本人或 ADMIN | 订单详情 |
| POST | `/api/orders/{id}/pay\|cancel` | 本人或 ADMIN | 支付 / 取消 |
| POST | `/api/orders/{id}/ship\|complete` | ADMIN | 发货 / 完成 |
| GET | `/api/orders/stats` | ADMIN | 按状态聚合 + 未取消金额合计 |
| GET | `/api/system/runtime` | 已认证 | 运行时自检（JDK、是否虚拟线程） |
| GET | `/actuator/health`、`/actuator/info` | 公开 | 健康检查与构建信息 |
| GET | `/actuator/metrics/**` | ADMIN | 运维指标（含端点清单与耗时画像，故不对普通用户开放） |

统一响应：`{"code":200,"message":"成功","data":{...},"timestamp":1791...}`；
错误：`{"code":40901,"message":"用户名已存在: alice","timestamp":...}`，HTTP 状态由业务码决定。

PATCH 采用"只提交变更字段"的语义：`null` 表示不修改，而接口返回的**脱敏值**（含 `***`）会被
400 拒绝 —— 因为 `@Email` 允许本地部分出现 `*`，若放行会把 `a***e@example.com` 当作真实邮箱写库，
且由于脱敏是幂等的，这种损坏在接口上不可见。

业务码编排为 `HTTP 状态 × 100 + 序号`（40000 参数校验、40101 凭据错误、40300 无权、40400 不存在、
40901 用户名冲突、40902 状态机非法、50000 服务端错误…）。

## 关键设计决策

| 决策 | 理由 | 代价 |
| --- | --- | --- |
| 统一响应包体 + 真实 HTTP 状态码 | 前端只需一套错误格式，同时保留 HTTP 语义（监控、网关可依据状态码告警） | 包体内 `code` 与 HTTP 状态存在冗余 |
| 越权访问他人订单返回 **404** 而非 403 | 避免通过状态码枚举他人资源；角色不足仍返回 403 | 与「403 更直观」的直觉不同，需在文档中说明 |
| JWT 无状态 + 关闭 CSRF | 不使用 Cookie 凭据即无 CSRF 攻击面 | 令牌在过期前无法单点撤销 |
| `HandlerMethodValidationException` 路径（不加 `@Validated`） | 让参数校验统一走 MVC 内建机制，返回 400 而非 AOP 的 `ConstraintViolationException`（500） | 控制器不能依赖 AOP 校验 |
| 排序字段白名单 | 既避免拼错字段静默改变顺序，也阻断属性路径注入 | 新增可排序字段需同步白名单 |
| `ddl-auto=validate` + Flyway | 表结构变更可审计、可回滚；测试与生产共用同一套迁移脚本 | 需维护 SQL |
| 领域不变量放在实体（`Order.pay()` 等） | 任何入口（HTTP、任务、消息）都无法绕过状态机与金额计算 | 实体不再是纯数据容器 |
| 实体公共父类承载审计字段 + 乐观锁 | 消除重复；`createdBy` 自动取自 JWT 主体 | 需要 `@EnableJpaAuditing` 与 `DateTimeProvider` |
| 时间统一 `Instant`(UTC) | 不依赖 JVM 时区，多区域部署不会错位 | 前端需本地化展示 |
| 出站 DTO 强制脱敏 | 手机号/邮箱默认打码，密码字段 `@JsonIgnore` 且从不进 DTO | 需要完整手机号时应另设受控接口 |
| `spring.jpa.open-in-view=false` | 懒加载必须在事务内完成，避免连接被视图层长期占用 | 转换必须在 Service 事务内完成 |
| 虚拟线程由 `spring.threads.virtual.enabled` 开启 | 无需手写执行器；`/api/system/runtime` 可实证请求线程与异步执行器均为虚拟线程 | Tomcat/JDBC 驱动需兼容（本项目已实证） |
| 每次请求回源校验账号状态（主键查询） | "禁用/删除账号"立即生效，不由令牌有效期兜底 | 每请求多一次主键查询；角色仍取自令牌 |
| 令牌同时校验 `iss` 与 `aud` | 令牌只能用于本 API，避免同密钥兄弟服务之间串用 | 签发端与资源端需共同维护这两个声明 |
| 仓库不保存固定密钥，dev 用进程内随机密钥 | 消除"用仓库里的密钥伪造管理员令牌"这一整类风险 | dev 重启后旧令牌失效（日志有 WARN 提示） |
| DTO 关闭隐式 profile 默认 | 打包产物必须显式指定环境，避免开发配置上线 | 直接 `java -jar` 需带 `--spring.profiles.active` |
| 密码按 UTF-8 字节校验（`@MaxBytes(72)`） | BCrypt 只处理前 72 字节，字符口径会让文档承诺与真实强度不一致 | 多字节密码可用长度更短 |
| 契约层拒绝"回填脱敏值"（`@NotMasked`） | 出站脱敏 + 入站校验必须成对，否则脱敏串会被当成真实值写库且无法察觉 | 客户端必须只提交变更字段 |
| 变更后先 `flush` 再映射 DTO | 审计时间戳在 flush 时写入，否则响应回显的是上一次的 `updateTime` | 事务内提前触发一条 UPDATE |
| 集合批量抓取（`default_batch_fetch_size`） | DTO 转换会访问 `Order.items` / `User.roles`，避免列表查询退化成 N+1 | 首屏查询可能多取一些相邻行 |
| 单价限制 13 位整数 | 50 行 × 999 数量仍能落在 `numeric(18,2)` 内，避免"校验通过但入库溢出" | 单件价格上限低于字段容量 |

## 数据与持久化

- **迁移**：`db/migration/V1__baseline_schema.sql`，dev/test/prod 共用；H2 以
  `MODE=PostgreSQL` 运行，因此同一份 DDL 在三处都能通过 Hibernate `validate`。
- **审计**：`create_time/update_time/created_by/updated_by` 由 `JpaAuditingConfig` 自动填充，
  未认证上下文（初始化脚本）记为 `system`。
- **乐观锁**：`BaseEntity.version`；并发修改抛出 `OptimisticLockingFailureException` → 409（40904）。
- **索引**：`t_order(user_id)`、`t_order(status)`、`t_order(create_time desc)`、`t_order_item(order_id)`。
- `t_order.user_id` 不建外键，保持订单与用户的聚合解耦（订单保留用户标识快照）。

## 生产注意事项

1. `JWT_SECRET` 应按 ≥32 字节强随机值注入（未注入时使用进程内随机密钥并在日志告警，仅适合本地）；
   当前实现为对称 HS256，生产建议改为统一认证中心签发（配置 `jwk-set-uri`）并移除本地 `JwtEncoder`。
2. **禁用/删除账号立即生效**：每个受保护请求都会按 `uid` 回源校验账号状态；但**角色变更不会立即生效**，
   需等令牌过期后重新签发（`demo.security.jwt.ttl` 默认 1h，可按需调短）。
3. `demo.security.allow-registration=false`（默认）关闭开放注册；`demo.seed.enabled=false` 不创建演示账号；
   `demo.security.allow-h2-console=false`（默认）不暴露 H2 控制台。
4. 接口文档在 prod 关闭；Actuator 仅暴露 `health,info,metrics`，且 `metrics` 需要 ADMIN 角色。
5. 登录接口对"账号不存在"与"密码错误"返回同一业务码，并都会执行一次 BCrypt 比较（消除时间差枚举）；
   如需进一步防护，建议叠加登录失败锁定与限流。
5. `pom.xml` 中 `<url/>`、`<licenses/>`、`<developers/>`、`<scm/>` 的空元素是刻意保留的：
   用于覆盖 `spring-boot-starter-parent` 继承来的同名元素。

## 测试组成（184 个）

| 层次 | 示例 | 验证内容 |
| --- | --- | --- |
| 纯单元 | `StringsTest`、`OrderNumberGeneratorTest`、`OrderStatusTest`、`PageResponseTest` | 脱敏、订单号格式与唯一性、状态机迁移矩阵、1 基页码换算 |
| 领域/转换 | `OrderTest`、`UserConverterTest`、`OrderConverterTest` | 金额推导、非法流转、脱敏与状态标签 |
| 服务（Mockito） | `UserServiceImplTest`、`OrderServiceImplTest`、`AuthServiceImplTest` | 重名冲突、密码摘要、数据级鉴权、统计聚合、令牌声明 |
| Web 切片（MockMvc + 安全链） | `UserApiIntegrationTest`、`OrderApiIntegrationTest` | HTTP 状态与业务码、字段校验明细、分页/排序白名单、越权 |
| 安全 | `SecurityApiIntegrationTest`、`RegistrationToggleTest` | 令牌签发/篡改/伪造/错误 `iss`·`aud` 拒绝、禁用即时生效、Actuator 分级授权、CORS 预检、注册开关 |
| 校验 | `PhoneValidationTest`、`MaxBytesTest`、`NotMaskedValidationTest` | 手机号格式、密码 UTF-8 字节上限（BCrypt 72 字节）、脱敏值回填拦截 |
| 真实容器 | `RealServerSmokeTest`（`RANDOM_PORT` + 真实 HTTP） | Tomcat 请求线程确为虚拟线程、真实 401/健康检查、订单全生命周期 |
| 持久化 | `PersistenceAuditingTest`、`DemoApplicationTests` | 审计字段与操作人、`@Version` 递增、Flyway + `validate` 上下文启动 |
| 配置回归 | `RandomSigningKeyRoundTripTest`、`RegistrationToggleTest` | 随机密钥下"签发即验签"、注册开关（测试库按上下文随机命名，避免跨上下文串数据） |

## 与原骨架的差异（补齐清单）

**修复的编译错误**：`ApiResponse` 的 `permits` 非法子句、`GlobalExceptionHandler` 未定义的 `log` 与
不存在的 `BusinessException.getCode()`、`CreateUserRequest.java` 内声明了两个 public 类型、
`BusinessException`/`NotFoundException` 不是异常类。

**补齐的空占位类**：`ErrorCode`（枚举业务码）、`BusinessException`/`NotFoundException`（异常体系）、
`Strings`（脱敏工具）、`UserStatus`/`OrderStatus`（枚举与状态机）、`Order` 实体、`OrderController`、
`CreateUserRequest`/`UpdateUserRequest`/`UserDTO`。

**替换的错误实现**：`UserServiceImpl<UserConverter>`（Spring 无法为类型变量注入依赖）→ 具体
`UserConverter` bean；`UserRepository.findAll(Specification)`（签名与 `JpaSpecificationExecutor`
冲突且不支持分页）→ 继承 `JpaSpecificationExecutor`；`JsonConfig` 返回 `null` 的
`Jackson3JsonFormatMapper` → `JsonMapperBuilderCustomizer`（Jackson 3）；`CorsConfig` 硬编码前端域名
→ 配置化并统一到 `CorsConfigurationSource`。

**删除的死代码**：空的 `Phone` 类（真实注解是 `@ValidPhone`）、空的 `VirtualThreadConfig`
（由 `spring.threads.virtual.enabled` 取代，且已用 `/api/system/runtime` 实证）。

**新增能力**：安全配置与 JWT 签发/校验（`iss`+`aud`+账号状态三重校验）、Flyway 迁移与三套 profile、
JPA 审计与乐观锁、订单聚合与状态机、统计聚合查询、OpenAPI 文档、184 个测试。

## 已知限制

- 角色变更需等令牌过期后重新签发（账号禁用则立即生效，见「生产注意事项」2）。
- 开放注册按设计会暴露"用户名是否已存在"（409），因此默认关闭；生产如需自助注册应叠加邮箱验证与限流。
- 登录/注册接口未实现限流与失败锁定，公网部署必须在前置网关补齐。
- 订单单价来自请求体（演示无商品目录表，已限制 13 位整数以保证总额不溢出 `numeric(18,2)`）；
  生产必须改为按 `productId` 取服务端权威价格并快照。
- 订单只支持「整单」状态流转，未实现退款、部分发货、库存扣减与支付对账。
- 未接入链路追踪/结构化日志（仅 Actuator + Micrometer 基础指标）。
- 对称密钥 HS256 仅适用于演示与内网；跨服务场景应改为非对称密钥或 OIDC。
# SpringBootDemo
