package h2ph.db;

import java.sql.Timestamp;

public class OfflinePlayerRecord {
    private final String uuid;
    private final String gamertag;
    private final Timestamp timeLeft;
    private final String lastLocation;

    public OfflinePlayerRecord(String uuid, String gamertag, Timestamp timeLeft, String lastLocation) {
        this.uuid = uuid;
        this.gamertag = gamertag;
        this.timeLeft = timeLeft;
        this.lastLocation = lastLocation;
    }

    public String getUuid() {
        return uuid;
    }

    public String getGamertag() {
        return gamertag;
    }

    public Timestamp getTimeLeft() {
        return timeLeft;
    }

    public String getLastLocation() {
        return lastLocation;
    }
}
