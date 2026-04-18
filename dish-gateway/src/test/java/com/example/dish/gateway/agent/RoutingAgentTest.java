package com.example.dish.gateway.agent;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.gateway.dto.ExtractedData;
import com.example.dish.gateway.service.IntentAndParameterExtractor;
import com.example.dish.gateway.service.SessionService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

class RoutingAgentTest {

    @Test
    void shouldKeepProvidedSessionIdAndStoreIdInContext() throws Exception {
        RoutingAgent routingAgent = new RoutingAgent();
        inject(routingAgent, "extractor", (IntentAndParameterExtractor) userInput ->
                new ExtractedData(IntentType.QUERY_ORDER, null, "12345", null));
        inject(routingAgent, "sessionService", (SessionService) (sessionId, requestStoreId) -> requestStoreId);

        RoutingDecision decision = routingAgent.route("帮我查订单12345", "SESSION_XYZ", "STORE_007", null);

        Assertions.assertEquals("SESSION_XYZ", decision.context().getSessionId());
        Assertions.assertEquals("STORE_007", decision.context().getStoreId());
        Assertions.assertEquals(IntentType.QUERY_ORDER, decision.intent());
        Assertions.assertEquals(RoutingDecision.TARGET_WORK_ORDER, decision.targetAgent());
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
