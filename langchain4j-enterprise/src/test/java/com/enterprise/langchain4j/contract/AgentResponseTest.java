package com.enterprise.langchain4j.contract;

import com.enterprise.langchain4j.context.AgentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentResponse 单元测试
 */
@DisplayName("AgentResponse 测试")
class AgentResponseTest {

    private AgentContext defaultContext;

    @BeforeEach
    void setUp() {
        defaultContext = AgentContext.createDefault();
    }

    @Nested
    @DisplayName("工厂方法测试")
    class FactoryMethodTests {

        @Test
        @DisplayName("success() 工厂方法应创建成功的响应")
        void testSuccessFactory() {
            AgentResponse response = AgentResponse.success("操作成功", "TestAgent", defaultContext);

            assertTrue(response.isSuccess());
            assertEquals("操作成功", response.getContent());
            assertEquals("TestAgent", response.getAgentName());
            assertSame(defaultContext, response.getContext());
            assertTrue(response.getFollowUpHints().isEmpty());
        }

        @Test
        @DisplayName("failure() 工厂方法应创建失败的响应")
        void testFailureFactory() {
            AgentResponse response = AgentResponse.failure("操作失败", "TestAgent", defaultContext);

            assertFalse(response.isSuccess());
            assertEquals("操作失败", response.getContent());
            assertEquals("TestAgent", response.getAgentName());
            assertSame(defaultContext, response.getContext());
        }

        @Test
        @DisplayName("success() 工厂方法应支持 null context")
        void testSuccessWithNullContext() {
            AgentResponse response = AgentResponse.success("操作成功", "TestAgent", null);

            assertTrue(response.isSuccess());
            assertEquals("操作成功", response.getContent());
            assertNull(response.getContext());
        }

        @Test
        @DisplayName("failure() 工厂方法应支持 null context")
        void testFailureWithNullContext() {
            AgentResponse response = AgentResponse.failure("操作失败", "TestAgent", null);

            assertFalse(response.isSuccess());
            assertEquals("操作失败", response.getContent());
            assertNull(response.getContext());
        }
    }

    @Nested
    @DisplayName("format() 输出格式测试")
    class FormatTests {

        @Test
        @DisplayName("format() 应返回原始内容当没有 followUpHints")
        void testFormatWithoutHints() {
            AgentResponse response = AgentResponse.success("测试内容", "TestAgent", defaultContext);
            String formatted = response.format();

            assertEquals("测试内容", formatted);
            assertFalse(formatted.contains("您可能还想"));
        }

        @Test
        @DisplayName("format() 应包含 followUpHints 当存在时")
        void testFormatWithHints() {
            AgentContext ctx = AgentContext.builder()
                .sessionId("test-session")
                .build();

            AgentResponse response = AgentResponse.builder()
                .success(true)
                .content("查询结果")
                .agentName("TestAgent")
                .context(ctx)
                .followUpHints(List.of("查看菜单", "联系客服"))
                .build();

            String formatted = response.format();

            assertTrue(formatted.contains("查询结果"));
            assertTrue(formatted.contains("您可能还想"));
            assertTrue(formatted.contains("查看菜单"));
            assertTrue(formatted.contains("联系客服"));
        }

        @Test
        @DisplayName("format() 应正确处理单个 followUpHint")
        void testFormatWithSingleHint() {
            AgentResponse response = AgentResponse.builder()
                .success(true)
                .content("结果")
                .agentName("TestAgent")
                .context(defaultContext)
                .followUpHints(List.of("单独提示"))
                .build();

            String formatted = response.format();

            assertTrue(formatted.contains("结果"));
            assertTrue(formatted.contains("单独提示"));
            // 验证格式中有 bullet point
            assertTrue(formatted.contains("•"));
        }
    }

    @Nested
    @DisplayName("followUpHints 添加测试")
    class FollowUpHintsTests {

        @Test
        @DisplayName("Builder.addFollowUpHint() 应添加提示到列表")
        void testAddFollowUpHint() {
            AgentResponse response = AgentResponse.builder()
                .success(true)
                .content("内容")
                .agentName("TestAgent")
                .addFollowUpHint("提示1")
                .addFollowUpHint("提示2")
                .build();

            assertEquals(2, response.getFollowUpHints().size());
            assertEquals("提示1", response.getFollowUpHints().get(0));
            assertEquals("提示2", response.getFollowUpHints().get(1));
        }

        @Test
        @DisplayName("Builder.addFollowUpHint() 应支持链式调用")
        void testAddFollowUpHintChain() {
            AgentResponse response = AgentResponse.builder()
                .success(true)
                .content("内容")
                .agentName("TestAgent")
                .addFollowUpHint("提示1")
                .addFollowUpHint("提示2")
                .addFollowUpHint("提示3")
                .build();

            assertEquals(3, response.getFollowUpHints().size());
        }

        @Test
        @DisplayName("followUpHints 默认应为空列表而非 null")
        void testFollowUpHintsDefaultEmpty() {
            AgentResponse response = AgentResponse.success("内容", "Agent", null);

            assertNotNull(response.getFollowUpHints());
            assertTrue(response.getFollowUpHints().isEmpty());
        }

        @Test
        @DisplayName("通过 followUpHints(List) 设置应为不可变列表")
        void testFollowUpHintsImmutable() {
            List<String> hints = new java.util.ArrayList<>();
            hints.add("原始提示");

            AgentResponse response = AgentResponse.builder()
                .success(true)
                .content("内容")
                .agentName("Agent")
                .followUpHints(hints)
                .build();

            // 修改原始列表不应影响 response
            hints.add("修改后的提示");

            assertEquals(1, response.getFollowUpHints().size());
            assertEquals("原始提示", response.getFollowUpHints().get(0));
        }
    }

    @Nested
    @DisplayName("Builder 模式测试")
    class BuilderTests {

        @Test
        @DisplayName("Builder 模式应正确构建对象")
        void testBuilderPattern() {
            AgentResponse response = AgentResponse.builder()
                .success(true)
                .content("测试内容")
                .agentName("TestAgent")
                .context(defaultContext)
                .build();

            assertTrue(response.isSuccess());
            assertEquals("测试内容", response.getContent());
            assertEquals("TestAgent", response.getAgentName());
            assertSame(defaultContext, response.getContext());
        }

        @Test
        @DisplayName("Builder 允许跳过可选字段")
        void testBuilderWithOptionalFields() {
            AgentResponse response = AgentResponse.builder()
                .success(false)
                .content("错误信息")
                .build();

            assertFalse(response.isSuccess());
            assertEquals("错误信息", response.getContent());
            assertNull(response.getAgentName());
            assertNull(response.getContext());
        }
    }
}
