@echo off
rem ============================================
rem agent-demo Windows launcher
rem 自动设置 UTF-8 防止中文乱码
rem ============================================
setlocal

rem 切到 UTF-8 代码页
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."
set "JAR=%PROJECT_ROOT%\target\agent-cli.jar"

if not exist "%JAR%" (
    echo [agent] 未找到 %JAR%
    echo        请先运行: mvn clean package -DskipTests
    exit /b 1
)

set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
java %JAVA_TOOL_OPTIONS% -jar "%JAR%" %*