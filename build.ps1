# 一键构建：前端(vue3+vite) → 拷入后端静态资源 → 后端(springboot) 打包
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

Write-Host '==== [1/3] npm run build (frontend) ===='
Push-Location frontend
npm run build
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'npm build failed' }
Pop-Location

Write-Host '==== [2/3] copy dist into backend resources ===='
$static = 'backend\src\main\resources\static'
if (Test-Path $static) { Remove-Item -Recurse -Force $static }
New-Item -ItemType Directory -Force $static | Out-Null
Copy-Item -Path 'frontend\dist\*' -Destination $static -Recurse -Force

Write-Host '==== [3/3] mvn package (backend) ===='
Push-Location backend
mvn -DskipTests package
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'maven package failed' }
Pop-Location

Write-Host '==== BUILD OK: backend\target\findchar-uchar.jar ===='
