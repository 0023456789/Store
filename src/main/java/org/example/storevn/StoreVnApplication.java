package org.example.storevn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StoreVnApplication {
    public static void main(String[] args) {
        SpringApplication.run(StoreVnApplication.class, args);
    }
}
