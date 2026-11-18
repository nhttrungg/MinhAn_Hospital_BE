package vn.antam.clinic.domain.event;

import java.util.Map;

/** Internal routing envelope. Never serialize this record onto a public connection. */
public record ClinicEvent(
    String type,
    Long roomId,
    Long doctorStaffId,
    Long userId,
    Map<String, Object> data
) {
    public ClinicEvent {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
