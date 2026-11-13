package edu.gla.cloudshield.web;

import edu.gla.cloudshield.core.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CloudShieldApplication {
  public static void main(String[] args) {
    SpringApplication.run(CloudShieldApplication.class, args);
  }

  @Bean
  RuntimeContext runtime() {
    RuntimeContext runtime = RuntimeContext.create(Config.fromEnv());
    if (!runtime.config().aws()) SampleData.seed(runtime.source());
    return runtime;
  }
}
