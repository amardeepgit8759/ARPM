package com.placefy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PlacefyApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlacefyApplication.class, args);
    }
}
