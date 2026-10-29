package com.hospitua.habitaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HabitacionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(HabitacionesApplication.class, args);
    }
}
