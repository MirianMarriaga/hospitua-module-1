package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.RegisterStaffAccountCommand;
import com.hospitua.habitaciones.application.ports.in.RegisterStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.out.PasswordEncoderPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import com.hospitua.habitaciones.infrastructure.adapters.in.startup.StaffAccountBootstrapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataUserAccountJpaRepository;
import com.hospitua.habitaciones.infrastructure.config.StaffBootstrapProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class StaffAccountIntegrationTest {

    @Autowired
    private StaffAccountRepositoryPort staffAccountRepository;

    @Autowired
    private SpringDataUserAccountJpaRepository jpaRepository;

    @Autowired
    private RegisterStaffAccountUseCase registerStaffAccountUseCase;

    @Autowired
    private PasswordEncoderPort passwordEncoder;

    @Autowired
    private StaffAccountBootstrapper bootstrapper;

    @Test
    @DisplayName("El bootstrapper crea la cuenta inicial de Administrador al arrancar")
    void shouldHaveCreatedInitialAdminAccount() {
        Optional<StaffAccount> adminOpt = staffAccountRepository.findByUsername("admin");
        assertThat(adminOpt).isPresent();

        StaffAccount admin = adminOpt.get();
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getFullName()).isEqualTo("Administrador");
        assertThat(admin.getArea()).isEqualTo(StaffArea.ADMINISTRACION);
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getCreatedBy()).isNull();
        assertThat(admin.getPasswordHash()).isNotBlank();
        assertThat(passwordEncoder.matches("admin123", admin.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("El bootstrapper es idempotente y no crea duplicados si ya existe un Administrador activo")
    void bootstrapperShouldBeIdempotent() {
        long countBefore = jpaRepository.count();
        bootstrapper.run(new DefaultApplicationArguments());
        long countAfter = jpaRepository.count();

        assertThat(countAfter).isEqualTo(countBefore);
    }

    @Test
    @Transactional
    @DisplayName("Guardar y leer cuenta del personal mediante el caso de uso y adaptador")
    void shouldRegisterAndRetrieveStaffAccount() {
        RegisterStaffAccountCommand command =
                new RegisterStaffAccountCommand("jramirez", "Limpieza2026", "Jorge Ramírez", StaffArea.LIMPIEZA);

        StaffAccount created = registerStaffAccountUseCase.register(command, "admin");
        assertThat(created).isNotNull();

        Optional<StaffAccount> found = staffAccountRepository.findByUsername("jramirez");
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("jramirez");
        assertThat(found.get().getArea()).isEqualTo(StaffArea.LIMPIEZA);
        assertThat(found.get().isActive()).isTrue();
        assertThat(found.get().getCreatedBy()).isEqualTo("admin");
        assertThat(passwordEncoder.matches("Limpieza2026", found.get().getPasswordHash()))
                .isTrue();
    }

    @Test
    @DisplayName("El bootstrapper rechaza el arranque si el usuario configurado no cumple el formato")
    void bootstrapperShouldFailWithInvalidUsername() {
        // Presencia y longitud de cada propiedad las valida StaffBootstrapProperties (ver StaffBootstrapPropertiesTest)
        StaffBootstrapProperties invalidProps = new StaffBootstrapProperties("ad", "Admin2026!", "Administrador");

        StaffAccountBootstrapper failingBootstrapper = new StaffAccountBootstrapper(
                new StaffAccountRepositoryPort() {
                    @Override
                    public Optional<StaffAccount> findByUsername(String username) {
                        return Optional.empty();
                    }

                    @Override
                    public StaffAccount save(StaffAccount account) {
                        return account;
                    }

                    @Override
                    public List<StaffAccount> findActiveAdministratorsForUpdate() {
                        return List.of(); // Sin administradores activos
                    }
                },
                registerStaffAccountUseCase,
                invalidProps);

        assertThatThrownBy(() -> failingBootstrapper.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }
}
