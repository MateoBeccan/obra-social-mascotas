package com.osmascotas.obrasocialmascotas.seguridad.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdministradorRunner implements ApplicationRunner {

    private final BootstrapAdministradorService bootstrapAdministradorService;

    public BootstrapAdministradorRunner(BootstrapAdministradorService bootstrapAdministradorService) {
        this.bootstrapAdministradorService = bootstrapAdministradorService;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapAdministradorService.inicializarSiCorresponde();
    }
}
