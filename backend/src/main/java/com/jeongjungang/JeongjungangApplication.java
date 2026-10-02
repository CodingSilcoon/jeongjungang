package com.jeongjungang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JeongjungangApplication {

    public static void main(String[] args) {
        SpringApplication.run(JeongjungangApplication.class, args);
    }
}
