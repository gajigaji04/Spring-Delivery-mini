package com.sparta.springdeliverymini;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
// JPA Auditing 활성화
@EnableJpaAuditing
public class SpringDeliveryMiniApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringDeliveryMiniApplication.class, args);
    }

}
