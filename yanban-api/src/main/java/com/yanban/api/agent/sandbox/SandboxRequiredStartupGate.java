package com.yanban.api.agent.sandbox;
import org.springframework.boot.ApplicationArguments; import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(prefix="yanban.sandbox",name="required-at-startup",havingValue="true")
final class SandboxRequiredStartupGate implements ApplicationRunner {
 private final SandboxBrokerClient broker;
 SandboxRequiredStartupGate(SandboxBrokerClient broker){this.broker=broker;}
 @Override public void run(ApplicationArguments args){broker.requireHealthy();}
}
