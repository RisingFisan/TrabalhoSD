import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stores the current and past positions of all the users in the system.
 */
public class Locations implements Serializable{

    /**
     * A representation of a user's position, using two integer values.
     */
    public static class Position implements Serializable {
        public int x;
        public int y;

        public Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public Position(Position pos) {
            this.x = pos.x;
            this.y = pos.y;
        }

        public Position(String line) throws IllegalStateException {
            Pattern pattern = Pattern.compile("(-?\\d+)[\\s,]+(-?\\d+)");
            Matcher matcher = pattern.matcher(line);
            if(matcher.find()) {
                this.x = Integer.parseInt(matcher.group(1));
                this.y = Integer.parseInt(matcher.group(2));
            }
            else {
                throw new IllegalStateException("Invalid coordinates");
            }
        }

        public byte[] toByteArray() {
            return String.format("%d %d", this.x, this.y).getBytes();
        }

        public static Position fromByteArray(byte[] a) {
            String[] coordinates = new String(a).split(" ");
            return new Position(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Position position = (Position) o;
            return this.x == position.x && this.y == position.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        public String toString() {
            return String.format("(%d,%d)",this.x,this.y);
        }

        public Position clone() { return new Position(this); }
    }

    private HashMap<Position, HashSet<String>> currentPositions;
    private HashMap<String, HashSet<Position>> history;
    public ReentrantReadWriteLock l = new ReentrantReadWriteLock();

    public Locations() {
        this.currentPositions = new HashMap<>();
        this.history = new HashMap<>();
    }

    /**
     * Moves a user to a new location, removing them from the old location.
     * @param username the user that moved.
     * @param newPos the user's new location.
     * @return the user's old location, or <i>null</i> if the user wasn't anywhere else before.
     */
    public Position moveUser(String username, Position newPos) {
        Position oldPos = null;
        for (Position pos : this.currentPositions.keySet()) {
            if (this.currentPositions.get(pos).remove(username)) {
                oldPos = pos;
                if (this.currentPositions.get(pos).size() == 0)
                    this.currentPositions.remove(pos);
                break;
            }
        }
        this.currentPositions.computeIfAbsent(newPos, (pos) -> new HashSet<>()).add(username);
        this.history.computeIfAbsent(username, (x) -> new HashSet<>()).add(new Position(newPos));
        return oldPos;
    }

    /**
     * Gets the number of users in a specified location.
     * @param pos the position to check for users.
     * @return the number of users in the specified location.
     */
    public int usersAtPos(Position pos) {
        HashSet<String> users = this.currentPositions.get(pos);
        if (users == null) return 0;
        else return users.size();
    }

    /**
     * Checks if two users are/were in the same location.
     * @param user1 the first user.
     * @param user2 the second user.
     * @return <i>true</i> if the two users were in contact, <i>false</i> otherwise.
     */
    public boolean wereInContact(String user1, String user2) {
        if (! history.containsKey(user1) || ! history.containsKey(user2))
            return false;
        HashSet<Position> commonPositions = new HashSet<>(history.get(user1));
        commonPositions.retainAll(history.get(user2));
        return commonPositions.size() > 0;
    }

    /**
     * Fetches the past locations of all users.
     * @return a <code>Map</code> associating each position with the users who passed through that location.
     */
    public Map<Position,Set<String>> getHistory() {
        Map<Position,Set<String>> result = new HashMap<>();
        for (String user : history.keySet()) {
            for (Position pos : history.get(user)) {
                result.computeIfAbsent(pos, e -> new HashSet<>()).add(user);
            }
        }
        return result;
    }

    public void serialize(String filepath) throws IOException {
        FileOutputStream fos = new FileOutputStream(filepath);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(this);
        oos.close();
        fos.close();
    }

    public static Locations deserialize(String filepath) throws IOException, ClassNotFoundException {
        FileInputStream fis = new FileInputStream(filepath);
        ObjectInputStream ois = new ObjectInputStream(fis);
        Locations locations = (Locations) ois.readObject();
        ois.close();
        fis.close();
        locations.currentPositions = new HashMap<>();
        return locations;
    }
}
