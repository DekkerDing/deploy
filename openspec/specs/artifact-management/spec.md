## Purpose

管理构建产出的交付物：入库落盘、平台属性声明（支持异构路由）、可移植性标注，以及列表与下载服务。

## Requirements

### Requirement: 制品入库落盘
构建成功后，系统 SHALL 将产出制品持久化到平台存储区（按项目/版本分目录），并登记元数据（文件名、大小、校验和、产生它的发布单）。

#### Scenario: 构建产物入库
- **WHEN** 一次 Maven 构建产出 `demo-app-1.0.0.jar`
- **THEN** 该文件被复制到项目的版本目录下，元数据入库，API 可查询到该制品

### Requirement: 制品平台描述符
每个制品 SHALL 声明平台属性：可移植制品标记为 `PORTABLE`（不含平台绑定内容）；平台绑定制品 MUST 声明目标 os/arch（必要时含 libc）。

#### Scenario: 纯 Java 制品标记为可移植
- **WHEN** 入库一个不含本地库的 JAR
- **THEN** 其平台描述符为 `PORTABLE`，可被路由到任意 os/arch 目标

#### Scenario: 平台绑定制品
- **WHEN** 入库一个含 Windows x86-64 本地库的 JAR 或原生二进制
- **THEN** 其描述符声明 `windows/amd64`，路由仅匹配该平台

### Requirement: 制品列表与下载
系统 SHALL 提供按项目/发布单列出制品、以及下载制品文件内容的接口。

#### Scenario: 列出制品
- **WHEN** 用户查询某项目的全部制品
- **THEN** 返回制品清单，含平台描述符与可移植性标志

#### Scenario: 下载制品
- **WHEN** 用户请求下载指定制品
- **THEN** 返回文件内容，且与入库时的校验和一致

### Requirement: 制品不可变
同一存储路径的制品内容 MUST NOT 被后续构建覆盖；重复版本 MUST 被拒绝或生成新版本号。

#### Scenario: 重复版本保护
- **WHEN** 新构建尝试覆盖已存在的同版本制品
- **THEN** 系统拒绝写入并返回冲突错误，原制品保持不变
