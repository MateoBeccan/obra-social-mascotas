package com.osmascotas.obrasocialmascotas;

import org.springframework.boot.SpringApplication;

public class TestObraSocialMascotasApplication {

	public static void main(String[] args) {
		SpringApplication.from(ObraSocialMascotasApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
