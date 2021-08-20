package com.miniso.ecomm.bootdemoapp;

import com.miniso.boot.autoconfiguration.annotation.EnableCat;
import com.miniso.boot.autoconfiguration.annotation.EnableMinisoDubbo;
import com.miniso.boot.autoconfiguration.apollo.EnableApollo;
import com.miniso.boot.autoconfiguration.xxljob.annotation.EnableXxlJob;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@EnableCat
@EnableApollo
@EnableXxlJob
@EnableMinisoDubbo
public class BootDemoAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootDemoAppApplication.class, args);
    }

    @GetMapping("/health")
    public String healthCheck() {
        return "OK";
    }
}
