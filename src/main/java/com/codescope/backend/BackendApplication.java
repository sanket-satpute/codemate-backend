package com.codescope.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableReactiveMongoRepositories(basePackages = {
    "com.codescope.backend.project.repository",
    "com.codescope.backend.chat",
    "com.codescope.backend.repository",
    "com.codescope.backend.upload.repository"
})
@EnableRedisRepositories(basePackages = {
    "com.codescope.backend.analysisjob.repository",
    "com.codescope.backend.chat"
})
@EnableAsync
public class BackendApplication {

	public static void main(String[] args) {
		Dotenv.configure().load();
		SpringApplication.run(BackendApplication.class, args);
		
}

}
