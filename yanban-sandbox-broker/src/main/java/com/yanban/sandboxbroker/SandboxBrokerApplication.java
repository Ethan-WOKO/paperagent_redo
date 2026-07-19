package com.yanban.sandboxbroker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class SandboxBrokerApplication {
    public static void main(String[] args) {
        SpringApplication app=new SpringApplication(SandboxBrokerApplication.class);
        String enabled=System.getProperty("yanban.broker.enabled",System.getenv().getOrDefault("YANBAN_SANDBOX_BROKER_ENABLED","false"));
        if(!Boolean.parseBoolean(enabled))app.setDefaultProperties(java.util.Map.of(
                "spring.main.web-application-type","none",
                "spring.autoconfigure.exclude","org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"));
        app.run(args);
    }
}
