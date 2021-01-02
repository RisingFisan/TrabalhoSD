import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Server {
    final static int WORKERS_PER_CONNECTION = 5;

    public static void main(String[] args) throws Exception {
        ServerSocket ss = new ServerSocket(12345);

        final Accounts accounts;
        final Locations locations;

        File f = new File("accounts.ser");
        if(!f.exists())
            accounts = new Accounts();
        else
            accounts = Accounts.deserialize("accounts.ser");

        f = new File("locations.ser");
        if(!f.exists())
            locations = new Locations();
        else
            locations = Locations.deserialize("locations.ser");

        ReentrantLock alarmsLock = new ReentrantLock();
        HashMap<Locations.Position, HashSet<AbstractMap.SimpleEntry<String,Condition>>> alarms = new HashMap<>();

        while(true) {
            Socket s = ss.accept();
            Connection c = new Connection(s);

            Runnable worker = () -> {
                try (c) {
                    while(true) {
                        Frame frame = c.receive();

                        if (frame.tag == 0) {
                            System.out.println("User log-in attempt.");
                            String email = frame.username;
                            String password = new String(frame.data);
                            String stored_password = null;
                            accounts.l.readLock().lock();
                            try {
                                stored_password = accounts.getPassword(email);
                            } finally {
                                accounts.l.readLock().unlock();
                            }
                            if (stored_password != null) {
                                if (stored_password.equals(password))
                                    c.send(0, "", "Sessão iniciada com sucesso!".getBytes());
                                else
                                    c.send(0, "", "Erro - palavra-passe errada.".getBytes());
                            } else
                                c.send(0, "", "Erro - conta não existe.".getBytes());
                        }
                        else if (frame.tag == 1) {
                            System.out.println("User registration attempt.");
                            String email = frame.username;
                            String password = new String(frame.data);
                            accounts.l.writeLock().lock();
                            try {
                                if(accounts.accountExists(email))
                                    c.send(1, "", "Erro - endereço de email já pertence a uma conta.".getBytes());
                                else {
                                    accounts.addAccount(email, password);
                                    accounts.serialize("accounts.ser");
                                    c.send(frame.tag, "", "Registo efetuado com sucesso!".getBytes());
                                }
                            } finally {
                                accounts.l.writeLock().unlock();
                            }
                        }
                        else if (frame.tag == 2) {
                            System.out.println("User location update.");
                            String[] coordinates = new String(frame.data).split(" ");
                            Locations.Position pos = new Locations.Position(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]));

                            locations.l.writeLock().lock();
                            try {
                                Locations.Position oldPos = locations.moveUser(frame.username, pos);
                                if (locations.usersAtPos(oldPos) == 0)
                                    alarmsLock.lock();
                                try {
                                    alarms.getOrDefault(oldPos, new HashSet<>())
                                            .stream()
                                            .map(AbstractMap.SimpleEntry::getValue)
                                            .forEach(Condition::signalAll);
                                } finally {
                                    alarmsLock.unlock();
                                }
                                locations.serialize("locations.ser");
                            }
                            finally {
                                locations.l.writeLock().unlock();
                            }
                            System.out.println(frame.username + " is now at location " + pos);
                        }
                        else if (frame.tag == 3) {
                            System.out.println("Location probing request.");
                            Locations.Position pos = Locations.Position.fromByteArray(frame.data);
                            int numUsers;
                            locations.l.readLock().lock();
                            try {
                                numUsers = locations.usersAtPos(pos);
                            }
                            finally {
                                locations.l.readLock().unlock();
                            }
                            c.send(3, "", String.valueOf(numUsers).getBytes());
                        }
                        else if (frame.tag == 30) {
                            Locations.Position pos = Locations.Position.fromByteArray(frame.data);
                            new Thread(() -> {
                                Condition cond = alarmsLock.newCondition();
                                alarmsLock.lock();
                                try {
                                    alarms.computeIfAbsent(pos, (p) -> new HashSet<>()).add(new AbstractMap.SimpleEntry<>(frame.username, cond));
                                    while (true) {
                                        int nUsers;
                                        locations.l.readLock().lock();
                                        try {
                                            nUsers = locations.usersAtPos(pos);
                                        }
                                        finally {
                                            locations.l.readLock().unlock();
                                        }
                                        if (! alarms.containsKey(pos) ||
                                                alarms.get(pos)
                                                        .stream()
                                                        .map(AbstractMap.SimpleEntry::getKey)
                                                        .noneMatch(s1 -> s1.equals(frame.username))) {
                                            c.send(30, "", new byte[0]);
                                            break;
                                        }
                                        if(nUsers == 0) {
                                            c.send(30, "", new byte[1]);
                                            break;
                                        }
                                        cond.await();
                                    }
                                }
                                catch (Exception ignored) {

                                }
                                finally {
                                    alarmsLock.unlock();
                                }
                            }).start();
                        }
                        else if (frame.tag == 99) {
                            if (frame.data.length > 0) {
                                // Fill this with what the server should do if the user reports that they're sick.
                            }
                            alarmsLock.lock();
                            try {
                                for (HashSet<AbstractMap.SimpleEntry<String, Condition>> set : alarms.values()) {
                                    Set<AbstractMap.SimpleEntry<String, Condition>> toRemove = set.stream().filter(e -> e.getKey().equals(frame.username)).collect(Collectors.toSet());
                                    set.removeAll(toRemove);
                                    toRemove.forEach(e -> e.getValue().signalAll());
                                }
                            }
                            finally {
                                alarmsLock.unlock();
                            }
                        }
                    }
                } catch (IOException ignored) {

                }
            };

            for (int i = 0; i < WORKERS_PER_CONNECTION; ++i)
                new Thread(worker).start();
        }

    }
}
