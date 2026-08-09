package com.bizco.server;

import com.bizco.server.config.DatabaseBootstrapInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BizcoServerApplication {

    public static void main(final String[] args) {
        final SpringApplication application = new SpringApplication(BizcoServerApplication.class);
        application.addInitializers(new DatabaseBootstrapInitializer());
        application.run(args);
    }
}
