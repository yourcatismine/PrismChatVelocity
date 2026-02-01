package h2ph.cache;

/**
 * Cached player data for fast team chat lookups.
 */
public class ProxyPlayerData {
    public boolean teamChatEnabled;
    public String teamId;
    public String teamName;

    public ProxyPlayerData() {
    }

    public ProxyPlayerData(boolean teamChatEnabled, String teamId, String teamName) {
        this.teamChatEnabled = teamChatEnabled;
        this.teamId = teamId;
        this.teamName = teamName;
    }
}
