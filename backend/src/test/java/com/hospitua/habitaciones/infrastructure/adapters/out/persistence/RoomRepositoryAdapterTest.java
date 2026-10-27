package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class RoomRepositoryAdapterTest {

    @Autowired
    private RoomRepositoryPort roomRepositoryPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Guardar y leer Room (ida y vuelta)")
    void shouldSaveAndFindRoom() {
        UUID id = UUID.randomUUID();
        Room room = new Room(
                id, "901", 9, RoomCategory.SUITE, 4, new BigDecimal("500000.00"), RoomStatus.Available, null, 0);

        Room saved = roomRepositoryPort.save(room);
        assertThat(saved.getId()).isEqualTo(id);

        Optional<Room> found = roomRepositoryPort.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getRoomNumber()).isEqualTo("901");
        assertThat(found.get().getCategoryRoom()).isEqualTo(RoomCategory.SUITE);
        assertThat(found.get().getBaseRate()).isEqualByComparingTo("500000.00");
        assertThat(found.get().getStatus()).isEqualTo(RoomStatus.Available);
    }

    @Test
    @DisplayName(
            "findByStatusIn y findByStatusNot devuelven en orden natural (101, 101A, 102, 1001) y filtran por estado")
    void shouldOrderInNaturalOrderAndFilterStatuses() {
        UUID id101A = UUID.randomUUID();
        UUID id1001 = UUID.randomUUID();

        // 101 y 102 ya existen en V2 como Available. Insertamos 101A y 1001 en Available.
        roomRepositoryPort.save(new Room(
                id101A,
                "101A",
                1,
                RoomCategory.SENCILLA,
                1,
                new BigDecimal("120000.00"),
                RoomStatus.Available,
                null,
                0));
        roomRepositoryPort.save(new Room(
                id1001, "1001", 10, RoomCategory.SUITE, 4, new BigDecimal("600000.00"), RoomStatus.Available, null, 0));

        List<Room> availableRooms = roomRepositoryPort.findByStatusIn(Set.of(RoomStatus.Available));
        List<String> numbers = availableRooms.stream().map(Room::getRoomNumber).toList();

        // Verificar que 101, 101A, 102 y 1001 aparecen en orden relativo estricto
        int idx101 = numbers.indexOf("101");
        int idx101A = numbers.indexOf("101A");
        int idx102 = numbers.indexOf("102");
        int idx1001 = numbers.indexOf("1001");

        assertThat(idx101).isGreaterThanOrEqualTo(0);
        assertThat(idx101A).isGreaterThan(idx101);
        assertThat(idx102).isGreaterThan(idx101A);
        assertThat(idx1001).isGreaterThan(idx102);

        // findByStatusNot debe excluir Inactive
        List<Room> nonInactive = roomRepositoryPort.findByStatusNot(RoomStatus.Inactive);
        assertThat(nonInactive).noneMatch(r -> r.getStatus() == RoomStatus.Inactive);
        assertThat(nonInactive).anyMatch(r -> r.getRoomNumber().equals("101"));
    }

    @Test
    @DisplayName("lockById devuelve vacío si no existe y bloquea la fila ante transacciones concurrentes")
    void shouldLockByIdAndBlockConcurrentTransactions() throws Exception {
        Optional<Room> nonExistent = roomRepositoryPort.lockById(UUID.randomUUID());
        assertThat(nonExistent).isEmpty();

        UUID roomId = UUID.randomUUID();
        roomRepositoryPort.save(new Room(
                roomId, "801", 8, RoomCategory.DOBLE, 2, new BigDecimal("250000.00"), RoomStatus.Available, null, 0));

        TransactionTemplate tx1 = new TransactionTemplate(transactionManager);
        TransactionTemplate tx2 = new TransactionTemplate(transactionManager);

        CountDownLatch tx1Locked = new CountDownLatch(1);
        CountDownLatch tx2Attempted = new CountDownLatch(1);
        CountDownLatch finishTx1 = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(2);

        // Hilo 1: Abre tx1 y adquiere lock pesimista sobre roomId
        Future<?> f1 = executor.submit(() -> {
            tx1.execute(status -> {
                Optional<Room> locked = roomRepositoryPort.lockById(roomId);
                assertThat(locked).isPresent();
                tx1Locked.countDown();
                try {
                    finishTx1.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        // Esperar a que Hilo 1 tenga el lock
        assertThat(tx1Locked.await(5, TimeUnit.SECONDS)).isTrue();

        // Hilo 2: Intenta lockById sobre el mismo roomId
        Future<Boolean> f2 = executor.submit(() -> {
            tx2Attempted.countDown();
            return tx2.execute(status -> {
                Optional<Room> locked = roomRepositoryPort.lockById(roomId);
                return locked.isPresent();
            });
        });

        assertThat(tx2Attempted.await(2, TimeUnit.SECONDS)).isTrue();

        // En este punto, Hilo 2 debe estar bloqueado esperando que Hilo 1 libere
        assertThatThrownBy(() -> f2.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        // Liberar Hilo 1 para que comitee la transacción
        finishTx1.countDown();
        f1.get(5, TimeUnit.SECONDS);

        // Ahora Hilo 2 debe poder terminar satisfactoriamente
        Boolean result2 = f2.get(5, TimeUnit.SECONDS);
        assertThat(result2).isTrue();

        executor.shutdown();
    }

    @Test
    @DisplayName(
            "Control optimista: actualización concurrente con la misma versión falla con ObjectOptimisticLockingFailureException")
    void shouldFailOptimisticLockingOnConcurrentUpdate() {
        UUID roomId = UUID.randomUUID();
        Room initial = roomRepositoryPort.save(new Room(
                roomId,
                "701",
                7,
                RoomCategory.SENCILLA,
                1,
                new BigDecimal("150000.00"),
                RoomStatus.Available,
                null,
                0));

        int version = initial.getVersion();

        // Primera actualización exitosa
        Room update1 = new Room(
                roomId,
                "701",
                7,
                RoomCategory.SENCILLA,
                1,
                new BigDecimal("160000.00"),
                RoomStatus.Available,
                null,
                version);
        Room saved1 = roomRepositoryPort.save(update1);
        assertThat(saved1.getVersion()).isGreaterThan(version);

        // Segunda actualización con la versión desactualizada
        Room staleUpdate = new Room(
                roomId,
                "701",
                7,
                RoomCategory.SENCILLA,
                1,
                new BigDecimal("170000.00"),
                RoomStatus.Available,
                null,
                version);

        assertThatThrownBy(() -> roomRepositoryPort.save(staleUpdate))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
