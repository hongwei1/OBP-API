第一步：创建一个独立的 Docker 网络
为了让 API 容器和 Redis 容器能够直接通过容器名相互通信，我们先创建一个桥接网络：

bash
docker network create obp-network
验证命令：运行 docker network ls，你应该能看到 obp-network 在列表中。
第二步：启动 Redis 缓存容器
在刚刚创建的网络中启动 Redis 服务，同时把 Redis 内部的 6379 端口映射到宿主机的 6380 端口：

bash
docker run -d \
  --name obp-redis \
  --network obp-network \
  -p 6380:6379 \
  redis:7-alpine redis-server --appendonly yes
命令拆解：
-d：在后台运行（Detached 模式）。
--name obp-redis：将容器命名为 obp-redis（后面 API 连接 Redis 就靠这个名字）。
--network obp-network：把容器加入我们第一步创建的网络。
-p 6380:6379：端口映射，宿主机 6380 -> 容器内 6379。
验证命令：运行 docker ps，确保 obp-redis 状态为 Up。
第三步：构建 OBP-API 镜像（以开发环境镜像为例）
在项目根目录下，指定 

Dockerfile.dev
 进行构建：

bash
docker build -t obp-api:dev -f development/docker/Dockerfile.dev .
命令拆解：
-t obp-api:dev：给生成的镜像打上标签名称 obp-api:dev。
-f development/docker/Dockerfile.dev：指定 Dockerfile 路径。
.：构建上下文（Context），即当前项目根目录。所有代码会根据它来拷贝。
说明：这一步因为要执行 mvn install 编译 Scala/Java 代码，可能会耗时几分钟。
第四步：启动 OBP-API 主服务容器
启动 OBP-API 容器，我们需要配置环境变量来告诉它如何连接 Redis 和 数据库：

bash
docker run -it \
  --name obp-api-app \
  --network obp-network \
  -p 8080:8080 \
  -e props.resource.dir=/app/props/ \
  -e JAVA_OPTS="-Drun.mode=production -Dprops.resource.dir=/app/props/" \
  -e OBP_CACHE_REDIS_URL=obp-redis \
  -e OBP_CACHE_REDIS_PORT=6379 \
  -e OBP_DB_URL="jdbc:postgresql://host.docker.internal:5432/obp_mapped?user=obp&password=yourpassword" \
  -v $(pwd)/src/main/resources/props:/app/props \
  --add-host=host.docker.internal:host-gateway \
  obp-api:dev
核心参数拆解：
-it：以交互式终端（Interactive + TTY）方式运行。这样你能在终端直接看到 Spring/Lift 的实时日志启动输出。
-p 8080:8080：将 API 服务暴露在宿主机的 8080 端口。
-e OBP_CACHE_REDIS_URL=obp-redis：传入环境变量，告诉 API 到刚才创建的 obp-redis 容器去访问 Redis。
-e OBP_DB_URL="..."：指定数据库连接。这里请将 yourpassword 替换为你本地 PostgreSQL 的真实密码。
-v $(pwd)/src/main/resources/props:/app/props：把宿主机的 props 配置文件目录挂载进容器，以便它能找到配置文件。
--add-host=host.docker.internal:host-gateway：关键参数，让容器内可以通过 host.docker.internal 这个域名访问到你宿主机上运行的数据库。
你可以先在终端中执行 第一步 和 第二步，如果有任何报错或疑问，随时把终端输出发给我！




# OBP-API Docker Development Setup

This Docker Compose setup provides a complete **live development environment** for OBP-API with Redis caching support and hot reloading capabilities.

## Services

### 🏦 **obp-api-app** 
- Main OBP-API application with **live development mode**
- Built with Maven 3.9.6 + OpenJDK 17
- Runs with Jetty Maven Plugin (`mvn jetty:run`)
- Port: `8080`
- **Features**: Hot reloading, incremental compilation, live props changes

### 🔴 **obp-api-redis**
- Redis cache server
- Version: Redis 7 Alpine
- Internal port: `6379`
- External port: `6380` (configurable)
- Persistent storage with AOF

## Quick Start

1. **Prerequisites**
   - Docker and Docker Compose installed
   - Local PostgreSQL database running
   - Props file configured at `src/main/resources/props/default.props`

2. **Start services**
   ```bash
   cd development/docker
   docker-compose up --build
   ```

3. **Access application**
   - OBP-API: http://localhost:8080
   - Redis: `localhost:6380`

## Configuration

### Database Connection

You can configure the database connection in multiple ways:

**Option 1: Props file** (traditional):
```properties
db.driver=org.postgresql.Driver
db.url=jdbc:postgresql://host.docker.internal:5432/obp_mapped?user=obp&password=yourpassword
```

**Option 2: Environment variables** (recommended for Docker):
The setup automatically overrides database settings via environment variables, so you can configure without modifying props files.

### Redis Configuration

Redis is configured automatically using OBP-API's environment variable override system:

```yaml
# Automatically set by docker-compose.yml:
OBP_CACHE_REDIS_URL=redis      # Connect to redis service
OBP_CACHE_REDIS_PORT=6379      # Internal Docker port
OBP_DB_URL=jdbc:postgresql://host.docker.internal:5432/obp_mapped?user=obp&password=f
```

### Custom Redis Port

To customize configuration, edit `.env`:

```bash
# .env file
OBP_CACHE_REDIS_PORT=6381
OBP_DB_URL=jdbc:postgresql://host.docker.internal:5432/mydb?user=myuser&password=mypass
```

Or set environment variables:

```bash
export OBP_CACHE_REDIS_PORT=6381
export OBP_DB_URL="jdbc:postgresql://host.docker.internal:5432/mydb?user=myuser&password=mypass"
docker-compose up --build
```

## Container Names

All containers use consistent `obp-api-*` naming:

- `obp-api-app` - Main application
- `obp-api-redis` - Redis cache server
- `obp-api-network` - Docker network
- `obp-api-redis-data` - Redis data volume

## Development Features

### Props File Override

The setup mounts your local props directory:
```yaml
volumes:
  - ../../src/main/resources/props:/app/props
```

Environment variables take precedence over props files using OBP's built-in system:
- `cache.redis.url` → `OBP_CACHE_REDIS_URL`
- `cache.redis.port` → `OBP_CACHE_REDIS_PORT`
- `db.url` → `OBP_DB_URL`

### Live Development Features

**🔥 Hot Reloading**: `Dockerfile.dev` uses `mvn jetty:run` for automatic recompilation and reloading:
- ✅ **Scala code changes** - Automatic recompilation and reload
- ✅ **Props file changes** - Live configuration updates via volume mount
- ✅ **Resource changes** - Instant refresh without container restart
- ✅ **Incremental builds** - Only changed files are recompiled

**Volume Mounts for Development**:
```yaml
# Automatically mounted by docker-compose:
volumes:
  - ../../src/main/resources/props:/app/props  # Live props updates
  # Source code is copied during build for optimal performance
```

## Useful Commands

### Service Management
```bash
# Start services
docker-compose up -d

# View logs
docker-compose logs obp-api-app
docker-compose logs obp-api-redis

# Stop services  
docker-compose down

# Rebuild and restart
docker-compose up --build
```

### Redis Operations
```bash
# Connect to Redis CLI
docker exec -it obp-api-redis redis-cli

# Check Redis keys
docker exec obp-api-redis redis-cli KEYS "*"

# Monitor Redis commands
docker exec obp-api-redis redis-cli MONITOR
```

### Container Inspection
```bash
# List containers
docker-compose ps

# Execute commands in containers
docker exec -it obp-api-app bash
docker exec -it obp-api-redis sh
```

## Troubleshooting

### Redis Connection Issues
- Check if `OBP_CACHE_REDIS_URL=redis` is set correctly
- Verify Redis container is running: `docker-compose ps`
- Test Redis connection: `docker exec obp-api-redis redis-cli ping`

### Database Connection Issues  
- Ensure local PostgreSQL is running
- Verify `host.docker.internal` resolves: `docker exec obp-api-app ping host.docker.internal`
- Check props file is mounted: `docker exec obp-api-app ls /app/props/`

### Props Loading Issues
- Check external props are detected: `docker-compose logs obp-api-app | grep "external props"`
- Verify environment variables: `docker exec obp-api-app env | grep OBP_`

## Environment Variables

The setup uses OBP-API's built-in environment override system:

| Props File Property | Environment Variable | Default | Description |
|---------------------|---------------------|---------|-------------|
| `cache.redis.url` | `OBP_CACHE_REDIS_URL` | `redis` | Redis hostname |
| `cache.redis.port` | `OBP_CACHE_REDIS_PORT` | `6379` | Redis port |
| `cache.redis.password` | `OBP_CACHE_REDIS_PASSWORD` | - | Redis password |
| `db.url` | `OBP_DB_URL` | `jdbc:postgresql://host.docker.internal:5432/obp_mapped?user=obp&password=f` | Database connection URL |

## Network Architecture

```
Host Machine
├── PostgreSQL :5432
├── Props Files (mounted) → Docker Container
└── Docker Network (obp-api-network)
    ├── obp-api-app :8080 → :8080 (Live Development Mode)
    └── obp-api-redis :6379 → :6380 (Persistent Cache)
```

**Connection Flow**:
- OBP-API ↔ Redis: Internal Docker network (`redis:6379`)
- OBP-API ↔ PostgreSQL: Host connection (`host.docker.internal:5432`) 
- Props Files: Live mounted from host (`/app/props/`)
- Redis External: Accessible via `localhost:6380`

## Development Benefits

### ⚡ **Live Development Mode** (`Dockerfile.dev`)
- **Single-stage build** optimized for development speed
- **Hot reloading** with `mvn jetty:run` - code changes are reflected instantly
- **Incremental compilation** - only changed files are rebuilt
- **Live props updates** - configuration changes without container restart
- **Security compliant** - selective file copying (SonarQube approved)

### 🔧 **Development vs Production**
- **Current setup**: Uses `Dockerfile.dev` for optimal development experience
- **Production ready**: Can switch to `Dockerfile` for multi-stage production builds
- **Best of both**: Live development with production-grade security practices

### 📋 **Additional Notes**
- Redis data persists in `obp-api-redis-data` volume
- Props files are live-mounted from host for instant updates
- Environment variables override props file values automatically
- Java 17 with proper module system compatibility
- All containers restart automatically unless stopped manually

---

🚀 **Ready for live development!** 

```bash
cd development/docker
docker-compose up --build
# Start coding - changes are reflected automatically! 🔥
```

**Pro Tips**:
- Make code changes and see them instantly without rebuilding
- Update props files and they're loaded immediately  
- Use `docker-compose logs obp-api -f` to watch live application logs
- Redis caching speeds up API responses significantly
