package com.ablsoft.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the ABLSoft product inventory import service.
 *
 * <p>Imported data lives in memory only: a successful import replaces the previous one and
 * everything is lost on restart.
 */
@SpringBootApplication
public class InventoryApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryApplication.class, args);
	}
}
