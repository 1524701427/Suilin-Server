package com.suilin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.suilin")
public class SuilinApplication {
    public static void main(String[] args) {
        SpringApplication.run(SuilinApplication.class, args);
    }
}
