# 本地 Docker 打包发布
# 用法: .\deploy-docker.ps1

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "需要 Docker Desktop，请先安装并启动"
}

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "已创建 .env，请按需填写 OPENAI_API_KEY"
}

Write-Host ">>> Maven 打包后端..."
Set-Location backend
mvn -B -DskipTests package -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Set-Location $Root

Write-Host ">>> 构建 Docker 镜像（使用本地基础镜像）..."
$env:DOCKER_BUILDKIT = "1"
docker compose build --pull=false
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ">>> 启动容器..."
docker compose up -d
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "部署完成: http://localhost:8765"
Write-Host "查看日志: docker compose logs -f backend"
