package com.hospitua.habitaciones.infrastructure.adapters.in.startup;

import com.hospitua.habitaciones.application.dto.RegisterStaffAccountCommand;
import com.hospitua.habitaciones.application.ports.in.RegisterStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import com.hospitua.habitaciones.infrastructure.config.StaffBootstrapProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Componente de inicio que crea la cuenta inicial de Administrador en la puesta en marcha si no existe
 * ninguna cuenta activa en dicha área (T022, FR-012).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaffAccountBootstrapper implements ApplicationRunner {

    private final StaffAccountRepositoryPort staffAccountRepository;
    private final RegisterStaffAccountUseCase registerStaffAccountUseCase;
    private final StaffBootstrapProperties bootstrapProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<StaffAccount> activeAdmins = staffAccountRepository.findActiveAdministratorsForUpdate();
        if (!activeAdmins.isEmpty()) {
            log.info(
                    "Se encontraron {} cuentas activas de Administración. No se requiere inicialización de cuenta inicial.",
                    activeAdmins.size());
            return;
        }

        log.info("No existen cuentas activas de Administración. Creando cuenta inicial desde la configuración...");
        validateBootstrapProperties();

        RegisterStaffAccountCommand command = new RegisterStaffAccountCommand(
                bootstrapProperties.getUsername(),
                bootstrapProperties.getPassword(),
                bootstrapProperties.getFullName(),
                StaffArea.ADMINISTRACION);

        registerStaffAccountUseCase.register(command, null);
        log.info(
                "Cuenta inicial de Administrador creada exitosamente con el usuario '{}'.",
                StaffAccount.normalizeUsername(bootstrapProperties.getUsername()));
    }

    private void validateBootstrapProperties() {
        String username = bootstrapProperties.getUsername();
        String password = bootstrapProperties.getPassword();
        String fullName = bootstrapProperties.getFullName();

        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                    "Falta la propiedad 'hospitua.bootstrap.admin.username' requerida para la puesta en marcha.");
        }

        try {
            StaffAccount.validateAndNormalizeUsername(username);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "El usuario de bootstrap no cumple las reglas de formato: " + ex.getMessage(), ex);
        }

        if (password == null || password.length() < 8) {
            throw new IllegalStateException(
                    "La contraseña de bootstrap debe tener al menos 8 caracteres (propiedad 'hospitua.bootstrap.admin.password').");
        }

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalStateException(
                    "Falta el nombre completo del administrador inicial (propiedad 'hospitua.bootstrap.admin.full-name').");
        }
    }
}
