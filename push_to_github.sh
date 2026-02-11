#!/bin/bash

# GitHub用户名
GITHUB_USER="muli0525"
REPO_NAME="ChineseChessPro"

# 创建GitHub仓库（需要手动创建的话，去 https://github.com/new 创建）
echo "=========================================="
echo "请先在浏览器打开以下链接创建仓库："
echo "https://github.com/new?name=$REPO_NAME"
echo ""
echo "创建时选择："
echo "  - Repository name: $REPO_NAME"
echo "  - Public 或 Private 都可以"
echo "  - 不要勾选 Add a README file"
echo ""
echo "创建完成后运行以下命令："
echo ""
echo "C:\\PROGRA~1\\Git\\bin\\git.exe remote add origin https://github.com/$GITHUB_USER/$REPO_NAME.git"
echo "C:\\PROGRA~1\\Git\\bin\\git.exe branch -M main"
echo "C:\\PROGRA~1\\Git\\bin\\git.exe push -u origin main"
echo ""
echo "=========================================="
