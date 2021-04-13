package com.miniso.ecomm.bootdemoapp;

import com.miniso.boot.autoconfiguration.annotation.EnableCat;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableCat
public class BootDemoAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootDemoAppApplication.class, args);
    }

}
