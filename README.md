# 文件共享服务器

基于局域网（手机热点）的班级文件共享系统，支持多班级隔离、文件上传下载、在线预览、分享链接等功能。

## 功能特性

- **用户系统**：登录/注册，第一个注册的用户自动成为管理员
- **班级管理**：管理员创建班级并生成邀请码，用户通过邀请码加入
- **文件隔离**：每个班级拥有独立的文件空间，互不干扰
- **文件操作**：上传、下载、删除、重命名、新建文件夹、搜索
- **批量操作**：批量选择、批量下载、批量删除
- **剪贴板**：文件复制/移动（跨目录）
- **去重检测**：上传时自动检测同名文件，提示是否覆盖
- **在线预览**：图片、文本、PDF 等文件直接预览
- **分享链接**：生成带有效期的文件分享链接
- **管理面板**：管理员可管理所有用户和班级
- **权限控制**：普通用户只能删除自己上传的文件，管理员可删除所有文件

## 技术栈

| 组件 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite |
| 后端 | Spring Boot 4 + Spring Security + JWT |
| 文件服务 | dufs (Rust) |
| 数据库 | MySQL 8.4 |
| 部署 | Docker Compose |

## 系统要求

- Docker 20.10+
- Docker Compose v2+
- JDK 21+（后端编译运行）
- Node.js 18+（前端编译运行）
- 至少 1GB 可用内存
- 至少 2GB 磁盘空间

## 快速开始

### 1. 克隆项目

```bash
git clone <仓库地址>
cd file_share_project
```

### 2. 启动基础容器

先启动 dufs 文件服务和 MySQL 数据库：

```bash
docker compose up -d
```

等待 MySQL 初始化完成（约 10 秒），可通过以下命令检查状态：

```bash
docker compose ps
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8081`。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端运行在 `http://localhost:5124`。

### 5. 访问系统

启动后通过浏览器访问：

```
http://localhost:5124
```

局域网内其他设备（同一手机热点下）通过电脑 IP 访问：

```
http://<电脑IP>:5124
```

查看电脑在热点网络中的 IP 地址：

```bash
# Linux/Mac
ip addr show | grep "inet "

# Windows
ipconfig
```

## 默认管理员账号

| 用户名 | 密码 |
|--------|------|
| root | seunet |

**首次登录后请立即修改密码或创建新的管理员账号。**

## 使用指南

### 管理员操作

1. **创建班级**：登录后点击"创建班级"按钮，输入班级名称
2. **查看邀请码**：每个班级卡片上显示邀请码，分享给学生即可
3. **管理用户**：进入"管理面板"查看所有用户和班级
4. **删除用户/班级**：在管理面板中操作
5. **删除文件**：管理员可以删除任何班级中的任何文件

### 普通用户操作

1. **注册账号**：在登录页切换到"注册"标签
2. **加入班级**：登录后点击"加入班级"，输入管理员提供的邀请码
3. **文件管理**：进入班级后可上传、下载、预览文件
4. **删除限制**：只能删除自己上传的文件
5. **分享文件**：点击文件的"分享"按钮生成下载链接

## 端口说明

| 端口 | 服务 | 说明 |
|------|------|------|
| 5124 | Vue 前端 | 用户访问入口 |
| 8081 | Spring Boot 后端 | API 服务 |
| 5000 | dufs | 文件存储服务 |
| 3306 | MySQL | 数据库 |

> 用户通过 5124 端口访问前端页面，前端自动代理 API 请求到后端 8081 端口。

## 项目结构

```
file_share_project/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/fileshare/
│   │   ├── config/          # 配置类（Security、CORS、数据初始化）
│   │   ├── controller/      # REST 控制器
│   │   ├── dto/             # 数据传输对象
│   │   ├── entity/          # JPA 实体
│   │   ├── exception/       # 全局异常处理
│   │   ├── repository/      # 数据访问层
│   │   ├── security/        # JWT 认证
│   │   ├── service/         # 业务逻辑
│   │   └── util/            # 工具类
│   └── pom.xml
├── frontend/                # Vue 3 前端
│   ├── src/
│   │   ├── App.vue          # 主界面
│   │   ├── Login.vue        # 登录/注册页
│   │   ├── api.js           # API 调用
│   │   └── style.css        # 全局样式
│   ├── nginx.conf           # Nginx 配置
│   └── package.json
├── dufs/data/               # 文件存储目录
├── mysql/data/              # 数据库持久化目录
└── docker-compose.yml       # 容器编排（dufs + mysql）
```

## 数据备份

- **数据库**：备份 `mysql/data/` 目录
- **文件**：备份 `dufs/data/` 目录

## 常见问题

### 上传失败提示"文件或目录不存在"

确保 dufs 容器正常运行：

```bash
docker compose ps dufs
```

### 局域网设备无法访问

1. 确认电脑和设备连接的是同一手机热点
2. 检查防火墙是否放行了 5124 端口：

```bash
# 临时关闭防火墙测试
sudo ufw allow 5124/tcp
```

### 忘记管理员密码

删除数据库中的用户记录，重启后端会自动重建默认管理员：

```bash
docker exec -it file-mysql mysql -u root -p'Root@2026!' fileshare -e "DELETE FROM users WHERE username='root';"
```

然后重启后端即可。

## 停止服务

```bash
# 停止 dufs 和 mysql 容器
docker compose down

# 停止并删除数据（谨慎）
docker compose down -v
```
