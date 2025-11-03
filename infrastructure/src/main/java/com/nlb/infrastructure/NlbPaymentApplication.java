package com.nlb.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.nlb")
@EnableJpaRepositories(basePackages = "com.nlb")
@EntityScan(basePackages = "com.nlb")
public class NlbPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(NlbPaymentApplication.class, args);
    }

}
