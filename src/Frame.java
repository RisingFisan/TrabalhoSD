public class Frame {
    public final int tag;
    public final String username;
    public final byte[] data;

    public Frame(int tag, String username, byte[] data) {
        this.tag = tag;
        this.username = username;
        this.data = data;
    }
}
