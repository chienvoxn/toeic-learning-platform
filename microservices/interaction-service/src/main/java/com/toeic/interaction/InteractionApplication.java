package com.toeic.interaction;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@org.springframework.amqp.rabbit.annotation.EnableRabbit
@SpringBootApplication public class InteractionApplication {
    public static void main(String[] args) { SpringApplication.run(InteractionApplication.class, args); }
}
