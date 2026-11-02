REM 一键构建：前端(vue3+vite) → 拷入后端静态资源 → 后端(springboot) 打包
@echo off
cd /d %~dp0

echo ==== [1/3] npm run build (frontend) ====
cd frontend
call npm run build || goto :err
cd ..

echo ==== [2/3] copy dist into backend resources ====
if exist backend\src\main\resources\static rmdir /s /q backend\src\main\resources\static
mkdir backend\src\main\resources\static
xcopy /e /y /i frontend\dist\* backend\src\main\resources\static\ >nul || goto :err

echo ==== [3/3] mvn package (backend) ====
cd backend
call mvn -DskipTests package || goto :err
cd ..

echo ==== BUILD OK: backend\target\findchar-uchar.jar ====
exit /b 0

:err
echo ==== BUILD FAILED ====
exit /b 1
