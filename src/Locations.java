import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Locations {

    public static class Position {
        public int x;
        public int y;

        public Position(int x, int y) {
            this.x = x;
            this.y = y;
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
    }

    private HashMap<Position, HashSet<String>> currentPositions;
    private HashMap<Position, ArrayList<String>> history;

    public Locations() {
        this.currentPositions = new HashMap<>();
        this.history = new HashMap<>();
    }

    public Position moveUser(String username, Position newPos) {
        Position oldPos = null;
        for (Position pos : this.currentPositions.keySet()) {
            if (this.currentPositions.get(pos).remove(username)) {
                oldPos = pos;
                break;
            }
        }
        this.currentPositions.computeIfAbsent(newPos, (pos) -> new HashSet<>()).add(username);
        this.history.computeIfAbsent(newPos, (pos) -> new ArrayList<>()).add(username);
        System.out.println(currentPositions.toString());
        return oldPos;
    }

    public int usersAtPos(Position pos) {
        HashSet<String> users = this.currentPositions.get(pos);
        if (users == null) return 0;
        else return users.size();
    }
}
