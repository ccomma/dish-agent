#!/bin/bash

# LangChain4j 示例运行脚本
# 用法: ./run_example.sh [示例名称]
# 示例名称: basic, prompt, rag, tool, local, structured

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 显示用法
usage() {
    echo -e "${GREEN}LangChain4j 示例运行脚本${NC}"
    echo "用法: $0 [示例名称]"
    echo ""
    echo "可用示例:"
    echo "  basic      - 基础对话示例 (BasicChatExample)"
    echo "  prompt     - 提示模板示例 (PromptTemplateExample)"
    echo "  rag        - RAG示例 (DocumentRAGExample)"
    echo "  tool       - 工具调用示例 (ToolCallingExample)"
    echo "  local      - 本地模型示例 (LocalModelExample)"
    echo "  structured - 结构化输出示例 (StructuredOutputExample)"
    echo "  all        - 运行所有示例"
    echo ""
    echo "示例:"
    echo "  $0 basic"
    echo "  $0 all"
}

# 检查环境变量
check_openai_key() {
    if [ -z "$OPENAI_API_KEY" ]; then
        echo -e "${YELLOW}警告: OPENAI_API_KEY 环境变量未设置${NC}"
        echo "部分示例需要 OpenAI API 密钥才能运行。"
        echo "请设置: export OPENAI_API_KEY='sk-...'"
        echo ""
        read -p "是否继续？(y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

# 编译项目
compile_project() {
    echo -e "${BLUE}编译项目...${NC}"
    if ! mvn compile -q; then
        echo -e "${RED}编译失败${NC}"
        exit 1
    fi
    echo -e "${GREEN}编译成功${NC}"
}

# 运行基础示例
run_basic() {
    echo -e "\n${BLUE}=== 运行基础对话示例 ===${NC}"
    check_openai_key
    mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.BasicChatExample" -q
}

# 运行提示模板示例
run_prompt() {
    echo -e "\n${BLUE}=== 运行提示模板示例 ===${NC}"
    check_openai_key
    mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.PromptTemplateExample" -q
}

# 运行RAG示例
run_rag() {
    echo -e "\n${BLUE}=== 运行RAG示例 ===${NC}"
    check_openai_key
    mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.DocumentRAGExample" -q
}

# 运行工具调用示例
run_tool() {
    echo -e "\n${BLUE}=== 运行工具调用示例 ===${NC}"
    check_openai_key
    mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.ToolCallingExample" -q
}

# 运行本地模型示例
run_local() {
    echo -e "\n${BLUE}=== 运行本地模型示例 ===${NC}"
    echo "注意: 此示例需要运行 Ollama 服务"
    echo "请确保已安装 Ollama 并运行: ollama serve"
    echo ""
    read -p "是否继续？(y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        return
    fi
    mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.LocalModelExample" -q
}

# 运行结构化输出示例
run_structured() {
    echo -e "\n${BLUE}=== 运行结构化输出示例 ===${NC}"
    check_openai_key
    mvn exec:java -Dexec.mainClass="com.example.langchain4jdemo.StructuredOutputExample" -q
}

# 运行所有示例
run_all() {
    echo -e "${GREEN}运行所有 LangChain4j 示例${NC}"
    echo "=================================="

    compile_project

    run_basic
    run_prompt
    run_rag
    run_tool
    run_local
    run_structured

    echo -e "\n${GREEN}所有示例运行完成！${NC}"
}

# 主程序
main() {
    if [ $# -eq 0 ]; then
        usage
        exit 1
    fi

    case "$1" in
        basic)
            compile_project
            run_basic
            ;;
        prompt)
            compile_project
            run_prompt
            ;;
        rag)
            compile_project
            run_rag
            ;;
        tool)
            compile_project
            run_tool
            ;;
        local)
            compile_project
            run_local
            ;;
        structured)
            compile_project
            run_structured
            ;;
        all)
            run_all
            ;;
        help|--help|-h)
            usage
            ;;
        *)
            echo -e "${RED}错误: 未知示例 '$1'${NC}"
            usage
            exit 1
            ;;
    esac
}

# 执行主程序
main "$@"