package com.hospitua.habitaciones;

import org.springframework.boot.SpringApplication;

public class TestHabitacionesApplication {

    public static void main(String[] args) {
        SpringApplication.from(HabitacionesApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("test")
                .run(args);
    }
}
