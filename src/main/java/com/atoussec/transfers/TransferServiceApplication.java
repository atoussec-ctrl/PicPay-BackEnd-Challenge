package com.atoussec.transfers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(proxyBeanMethods = false)
public class TransferServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(TransferServiceApplication.class, args);
  }
}
