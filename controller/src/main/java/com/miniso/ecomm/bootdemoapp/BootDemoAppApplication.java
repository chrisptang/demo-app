package com.miniso.ecomm.bootdemoapp;

import com.miniso.boot.autoconfiguration.annotation.EnableCat;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@EnableCat
public class BootDemoAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootDemoAppApplication.class, args);
    }

    @GetMapping("/health")
    public String healthCheck() {
        return "OK";
    }
}
