# 本机一键部署（Windows PowerShell）
# 用法: .\deploy.ps1

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Error "需要 Node.js 18+，请从 https://nodejs.org 安装"
}
Write-Host "Node $(node --version)"

if (-not (Test-Path "node_modules")) {
    Write-Host "安装 npm 依赖..."
    npm install --no-fund --no-audit
}

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "已创建 .env（正式 LLM 生成需填写 OPENAI_API_KEY + 安装 Python）"
}

Write-Host "`n=== 试运行 demo_novel（不调用 API）==="
node web/server.mjs --dry-run-cli --project demo_novel

Write-Host "`n=== 启动 Web: http://127.0.0.1:8765 ==="
Write-Host "选择项目 demo_novel → 点击「试运行」`n"
npm start
