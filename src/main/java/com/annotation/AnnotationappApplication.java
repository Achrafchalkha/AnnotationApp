package com.annotation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AnnotationappApplication {

	public static void main(String[] args) {
		SpringApplication.run(AnnotationappApplication.class, args);
	}

}
