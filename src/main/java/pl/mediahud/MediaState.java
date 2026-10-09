package pl.mediahud;

public final class MediaState {
    public final String title;
    public final String artist;
    public final boolean playing;
    public final long posMs;
    public final long durMs;
    private final long recvNano = System.nanoTime();

    public MediaState(String title, String artist, boolean playing, long posMs, long durMs) {
        this.title = title;
        this.artist = artist;
        this.playing = playing;
        this.posMs = posMs;
        this.durMs = durMs;
    }

    /** Aktualna pozycja utworu (ekstrapolowana miedzy odczytami). */
    public long currentPosMs() {
        long p = posMs;
        if (playing) p += (System.nanoTime() - recvNano) / 1_000_000L;
        if (durMs > 0 && p > durMs) p = durMs;
        return p;
    }
}
