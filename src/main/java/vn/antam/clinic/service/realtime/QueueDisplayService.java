package vn.antam.clinic.service.realtime;

import java.util.*;

/** Business contract; implemented by the Spring-managed QueueDisplayServiceImpl. */
public interface QueueDisplayService {
    public record Ticket(int queueNumber, String displayName, String calledAt, int callCount) {}

    public record RoomBoard(long roomId, String roomName, Ticket current, List<Ticket> next) {}

    public record Board(String clinicName, String generatedAt, List<RoomBoard> rooms) {}

    public boolean enabled();

    public void requireEnabled();

    public Board board(Set<Long> roomIds);
}
