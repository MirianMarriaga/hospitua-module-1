package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.domain.model.Room;
import java.util.Comparator;

/**
 * Orden natural de {@code room_number} (T008): primero la parte numérica y después el sufijo, p. ej. 101, 101A, 102, 1001.
 */
final class RoomNumberOrder {

    static final Comparator<String> NUMBERS =
            Comparator.comparingLong(RoomNumberOrder::numericPart).thenComparing(RoomNumberOrder::suffix);

    static final Comparator<Room> NATURAL = Comparator.comparing(Room::getRoomNumber, NUMBERS);

    private RoomNumberOrder() {}

    private static long numericPart(String roomNumber) {
        int digits = leadingDigits(roomNumber);
        return digits == 0 ? 0 : Long.parseLong(roomNumber.substring(0, digits));
    }

    private static String suffix(String roomNumber) {
        return roomNumber.substring(leadingDigits(roomNumber));
    }

    private static int leadingDigits(String roomNumber) {
        int i = 0;
        while (i < roomNumber.length() && Character.isDigit(roomNumber.charAt(i))) {
            i++;
        }
        return i;
    }
}
