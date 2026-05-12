package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.AgentExecutionStep;
import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.common.runtime.PolicyDecision;

import java.util.List;

/**
 * 策略门禁接口。
 * 负责把 gateway step 转成 policy 请求，并提供允许执行、等待审批两类判断。
 */
interface PolicyGatekeeper {

    List<AgentExecutionStep> filterAllowedSteps(List<AgentExecutionStep> steps,
                                                RoutingDecision routing,
                                                String traceId);

    AgentExecutionStep findFirstApprovalRequiredStep(List<AgentExecutionStep> steps,
                                                      RoutingDecision routing,
                                                      String traceId);

    PolicyDecision evaluate(AgentExecutionStep step,
                            RoutingDecision routing,
                            String traceId);
}
