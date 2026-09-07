package com.suilin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
    "com.suilin.user.mapper",
    "com.suilin.elder.mapper",
    "com.suilin.reminder.mapper",
    "com.suilin.health.mapper",
    "com.suilin.device.mapper",
    "com.suilin.sos.mapper"
})
public class SuilinApplication {
    public static void main(String[] args) {
        SpringApplication.run(SuilinApplication.class, args);
    }
}
