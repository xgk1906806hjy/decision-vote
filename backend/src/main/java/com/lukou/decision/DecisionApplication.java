package com.lukou.decision;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.lukou.decision.mapper")
public class DecisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionApplication.class, args);
    }
}
