package com.yanban.api.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpSandboxBrokerClientTest {
    private HttpServer server;

    @AfterEach void stop(){if(server!=null)server.stop(0);}

    @Test void preservesConflictPayloadAndUnavailableSemantics() throws Exception {
        assertFailure(409,"{\"code\":\"DIGEST_CONFLICT\",\"message\":\"x\"}",SandboxFailureCode.RECEIPT_CONFLICT);
        assertFailure(413,"{\"code\":\"REQUEST_TOO_LARGE\",\"message\":\"x\"}",SandboxFailureCode.PROVIDER_REJECTED);
        assertFailure(503,"{\"code\":\"PROVIDER_UNAVAILABLE\",\"message\":\"x\"}",SandboxFailureCode.SANDBOX_UNAVAILABLE);
    }

    private void assertFailure(int status,String body,SandboxFailureCode code)throws Exception{
        if(server!=null)server.stop(0);server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/v1/executions/x",exchange->{byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        SandboxExecutionProperties properties=new SandboxExecutionProperties();properties.setBrokerUrl(URI.create("http://127.0.0.1:"+server.getAddress().getPort()));properties.setBrokerToken("x".repeat(32));
        HttpSandboxBrokerClient client=new HttpSandboxBrokerClient(RestClient.builder(),properties,new ObjectMapper());
        assertThatThrownBy(()->client.status("x")).isInstanceOf(SandboxExecutionException.class)
                .extracting(ex->((SandboxExecutionException)ex).code()).isEqualTo(code);
    }
}
