package com.enterprise.langchain4j.context;

import com.enterprise.langchain4j.classifier.IntentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentContext 单元测试
 */
@DisplayName("AgentContext 测试")
class AgentContextTest {

    private AgentContext defaultContext;

    @BeforeEach
    void setUp() {
        defaultContext = AgentContext.createDefault();
    }

    @Nested
    @DisplayName("Builder 模式测试")
    class BuilderTests {

        @Test
        @DisplayName("Builder 模式应正确构建 AgentContext")
        void testBuilderPattern() {
            AgentContext context = AgentContext.builder()
                .sessionId("test-session-123")
                .intent(IntentType.DISH_QUESTION)
                .userInput("宫保鸡丁的做法")
                .storeId("store-001")
                .orderId("order-456")
                .dishName("宫保鸡丁")
                .refundReason("不想要了")
                .build();

            assertEquals("test-session-123", context.getSessionId());
            assertEquals(IntentType.DISH_QUESTION, context.getIntent());
            assertEquals("宫保鸡丁的做法", context.getUserInput());
            assertEquals("store-001", context.getStoreId());
            assertEquals("order-456", context.getOrderId());
            assertEquals("宫保鸡丁", context.getDishName());
            assertEquals("不想要了", context.getRefundReason());
        }

        @Test
        @DisplayName("Builder 应支持只设置必需的字段")
        void testBuilderWithMinimalFields() {
            AgentContext context = AgentContext.builder()
                .sessionId("minimal-session")
                .build();

            assertEquals("minimal-session", context.getSessionId());
            assertNull(context.getIntent());
            assertNull(context.getUserInput());
            assertNull(context.getStoreId());
            assertNull(context.getOrderId());
            assertNull(context.getDishName());
            assertNull(context.getRefundReason());
            assertNotNull(context.getMetadata());
            assertTrue(context.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("Builder 应支持 metadata")
        void testBuilderWithMetadata() {
            Map<String, Object> meta = new HashMap<>();
            meta.put("key1", "value1");
            meta.put("key2", 123);

            AgentContext context = AgentContext.builder()
                .sessionId("test-session")
                .metadata(meta)
                .build();

            assertEquals("value1", context.getMetadata().get("key1"));
            assertEquals(123, context.getMetadata().get("key2"));
        }

        @Test
        @DisplayName("Builder 方法应返回 Builder 实例以支持链式调用")
        void testBuilderMethodsReturnBuilder() {
            AgentContext.Builder builder = AgentContext.builder();
            AgentContext.Builder result = builder.sessionId("test");

            assertSame(builder, result);
        }
    }

    @Nested
    @DisplayName("createDefault() 方法测试")
    class CreateDefaultTests {

        @Test
        @DisplayName("createDefault() 应生成有效的 AgentContext")
        void testCreateDefault() {
            AgentContext context = AgentContext.createDefault();

            assertNotNull(context);
            assertNotNull(context.getSessionId());
            assertTrue(context.getSessionId().startsWith("SESSION_"));
        }

        @Test
        @DisplayName("createDefault() 生成的 sessionId 应为 8 位 UUID")
        void testCreateDefaultSessionIdFormat() {
            AgentContext context = AgentContext.createDefault();

            // SESSION_ + 8位UUID
            String sessionId = context.getSessionId();
            assertTrue(sessionId.startsWith("SESSION_"));
            // SESSION_ = 7 letters + 1 underscore = 8 chars, UUID substring = 8 chars, total = 16
            assertEquals(16, sessionId.length());
        }

        @Test
        @DisplayName("createDefault() 每次调用应生成不同的 sessionId")
        void testCreateDefaultUniqueSessionIds() {
            AgentContext context1 = AgentContext.createDefault();
            AgentContext context2 = AgentContext.createDefault();

            assertNotEquals(context1.getSessionId(), context2.getSessionId());
        }

        @Test
        @DisplayName("createDefault() 创建的 context 其他字段应为 null")
        void testCreateDefaultOtherFieldsNull() {
            AgentContext context = AgentContext.createDefault();

            assertNull(context.getIntent());
            assertNull(context.getUserInput());
            assertNull(context.getStoreId());
            assertNull(context.getOrderId());
            assertNull(context.getDishName());
            assertNull(context.getRefundReason());
            assertNotNull(context.getMetadata());
        }
    }

    @Nested
    @DisplayName("withXxx() 方法不可变性测试")
    class ImmutabilityTests {

        @Test
        @DisplayName("withIntent() 应返回新实例而非修改原实例")
        void testWithIntentReturnsNewInstance() {
            String originalSessionId = defaultContext.getSessionId();
            AgentContext newContext = defaultContext.withIntent(IntentType.DISH_QUESTION);

            assertNotSame(defaultContext, newContext);
            assertEquals(originalSessionId, newContext.getSessionId());
            assertEquals(IntentType.DISH_QUESTION, newContext.getIntent());
            assertNull(defaultContext.getIntent()); // 原实例不变
        }

        @Test
        @DisplayName("withUserInput() 应返回新实例而非修改原实例")
        void testWithUserInputReturnsNewInstance() {
            AgentContext newContext = defaultContext.withUserInput("新的输入");

            assertNotSame(defaultContext, newContext);
            assertEquals("新的输入", newContext.getUserInput());
            assertNull(defaultContext.getUserInput()); // 原实例不变
        }

        @Test
        @DisplayName("withStoreId() 应返回新实例而非修改原实例")
        void testWithStoreIdReturnsNewInstance() {
            AgentContext newContext = defaultContext.withStoreId("store-999");

            assertNotSame(defaultContext, newContext);
            assertEquals("store-999", newContext.getStoreId());
            assertNull(defaultContext.getStoreId()); // 原实例不变
        }

        @Test
        @DisplayName("多次 withXxx() 调用应保持不变性")
        void testMultipleWithCallsMaintainImmutability() {
            AgentContext ctx1 = defaultContext.withIntent(IntentType.DISH_QUESTION);
            AgentContext ctx2 = ctx1.withUserInput("用户输入");
            AgentContext ctx3 = ctx2.withStoreId("store-001");

            // 验证每个实例都是独立的
            assertNotSame(defaultContext, ctx1);
            assertNotSame(ctx1, ctx2);
            assertNotSame(ctx2, ctx3);

            // 验证原始实例未被修改
            assertNull(defaultContext.getIntent());
            assertNull(defaultContext.getUserInput());
            assertNull(defaultContext.getStoreId());

            // 验证链式实例的字段累积
            assertEquals(IntentType.DISH_QUESTION, ctx1.getIntent());
            assertEquals(IntentType.DISH_QUESTION, ctx2.getIntent());
            assertEquals(IntentType.DISH_QUESTION, ctx3.getIntent());

            assertNull(ctx1.getUserInput());
            assertEquals("用户输入", ctx2.getUserInput());
            assertEquals("用户输入", ctx3.getUserInput());

            assertNull(ctx2.getStoreId());
            assertEquals("store-001", ctx3.getStoreId());
        }

        @Test
        @DisplayName("withXxx() 方法应保留未修改的字段")
        void testWithXxxPreservesOtherFields() {
            AgentContext original = AgentContext.builder()
                .sessionId("session-123")
                .intent(IntentType.QUERY_ORDER)
                .userInput("查询订单")
                .storeId("store-001")
                .orderId("order-002")
                .dishName("麻婆豆腐")
                .refundReason("reason")
                .build();

            AgentContext newContext = original.withIntent(IntentType.DISH_QUESTION);

            // 验证保留的字段
            assertEquals("session-123", newContext.getSessionId());
            assertEquals(IntentType.DISH_QUESTION, newContext.getIntent());
            assertEquals("查询订单", newContext.getUserInput());
            assertEquals("store-001", newContext.getStoreId());
            assertEquals("order-002", newContext.getOrderId());
            assertEquals("麻婆豆腐", newContext.getDishName());
            assertEquals("reason", newContext.getRefundReason());
        }
    }

    @Nested
    @DisplayName("metadata 默认行为测试")
    class MetadataDefaultTests {

        @Test
        @DisplayName("未设置 metadata 时应返回空 Map 而非 null")
        void testMetadataDefaultsToEmptyMap() {
            AgentContext context = AgentContext.builder()
                .sessionId("test")
                .build();

            assertNotNull(context.getMetadata());
            assertTrue(context.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("Builder 中设置 null metadata 应转换为空 Map")
        void testNullMetadataBecomesEmptyMap() {
            AgentContext context = AgentContext.builder()
                .sessionId("test")
                .metadata(null)
                .build();

            assertNotNull(context.getMetadata());
            assertTrue(context.getMetadata().isEmpty());
        }
    }

    @Nested
    @DisplayName("toString() 测试")
    class ToStringTests {

        @Test
        @DisplayName("toString() 应包含关键字段信息")
        void testToStringContainsFields() {
            AgentContext context = AgentContext.builder()
                .sessionId("test-session")
                .intent(IntentType.DISH_QUESTION)
                .storeId("store-001")
                .orderId("order-002")
                .dishName("宫保鸡丁")
                .refundReason("不想要了")
                .build();

            String str = context.toString();

            assertTrue(str.contains("AgentContext"));
            assertTrue(str.contains("test-session"));
            assertTrue(str.contains("DISH_QUESTION"));
            assertTrue(str.contains("store-001"));
            assertTrue(str.contains("order-002"));
            assertTrue(str.contains("宫保鸡丁"));
            assertTrue(str.contains("不想要了"));
        }
    }

    @Nested
    @DisplayName("字段访问测试")
    class FieldAccessTests {

        @Test
        @DisplayName("所有 getter 方法应返回正确值")
        void testGetters() {
            Map<String, Object> meta = new HashMap<>();
            meta.put("testKey", "testValue");

            AgentContext context = AgentContext.builder()
                .sessionId("sess-123")
                .intent(IntentType.GREETING)
                .userInput("你好")
                .storeId("store-abc")
                .orderId("order-xyz")
                .dishName("鱼香肉丝")
                .refundReason("太辣了")
                .metadata(meta)
                .build();

            assertEquals("sess-123", context.getSessionId());
            assertEquals(IntentType.GREETING, context.getIntent());
            assertEquals("你好", context.getUserInput());
            assertEquals("store-abc", context.getStoreId());
            assertEquals("order-xyz", context.getOrderId());
            assertEquals("鱼香肉丝", context.getDishName());
            assertEquals("太辣了", context.getRefundReason());
            assertEquals("testValue", context.getMetadata().get("testKey"));
        }
    }
}
