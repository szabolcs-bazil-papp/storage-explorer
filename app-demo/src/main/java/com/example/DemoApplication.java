package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import com.aestallon.storageexplorer.springstarter.EnableStorageExplorer;

@SpringBootApplication(exclude = { ErrorMvcAutoConfiguration.class })
@EnableStorageExplorer
public class DemoApplication {

  public static void main(String[] args) {
    SpringApplication.run(DemoApplication.class, args);
  }
}
