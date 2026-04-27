package com.example.langchain4jdemo.rag;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 教学演示中的 RAG 测试会真实访问外部模型 API，默认不纳入本地单元测试基线。
 */
final class DemoIntegrationTestSupport {

    private static final String ENABLED_FLAG = "RUN_LANGCHAIN4J_DEMO_INTEGRATION";

    private DemoIntegrationTestSupport() {
    }

    static void requireEnabled() {
        assumeTrue(isEnabled(), "设置 RUN_LANGCHAIN4J_DEMO_INTEGRATION=true 后运行外部 API 集成测试");
    }

    private static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_FLAG, System.getenv(ENABLED_FLAG)));
    }
}
