package io.github.minunglee.gateway;

import org.springframework.boot.SpringApplication;

public class TestSpringLlmGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.from(SpringLlmGatewayApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
