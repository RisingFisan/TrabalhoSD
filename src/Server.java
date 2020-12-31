import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class Server {
    final static int WORKERS_PER_CONNECTION = 5;

    public static void main(String[] args) throws Exception {
        ServerSocket ss = new ServerSocket(12345);

        File f = new File("accounts.ser");
        if(!f.exists()) {
            FileOutputStream fos = new FileOutputStream("accounts.ser");
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(new Accounts());
            oos.close();
            fos.close();
        }
        FileInputStream fis = new FileInputStream("accounts.ser");
        ObjectInputStream ois = new ObjectInputStream(fis);

        Accounts accounts = (Accounts) ois.readObject();

        ois.close();
        fis.close();

        Locations locations = new Locations();
        ReentrantLock alarmsLock = new ReentrantLock();
        HashMap<Locations.Position, HashSet<Condition>> alarms = new HashMap<>();

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
                                    c.send(0, "", "Logged in successfully!".getBytes());
                                else
                                    c.send(0, "", "Error - wrong password.".getBytes());
                            } else
                                c.send(0, "", "Error - account does not exist.".getBytes());
                        }
                        else if (frame.tag == 1) {
                            System.out.println("User registration attempt.");
                            String email = frame.username;
                            String password = new String(frame.data);
                            accounts.l.writeLock().lock();
                            try {
                                if(accounts.accountExists(email))
                                    c.send(1, "", "Error - email address already tied to an account.".getBytes());
                                else {
                                    accounts.addAccount(email, password);
                                    accounts.serialize("accounts.ser");
                                    c.send(frame.tag, "", "Registration was successful!".getBytes());
                                }
                            } finally {
                                accounts.l.writeLock().unlock();
                            }
                        }
                        else if (frame.tag == 2) {
                            System.out.println("User location update.");
                            String[] coordinates = new String(frame.data).split(" ");
                            Locations.Position pos = new Locations.Position(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]));
                            Locations.Position oldPos = locations.moveUser(frame.username, pos);
                            if (locations.usersAtPos(oldPos) == 0)
                                alarmsLock.lock();
                                try {
                                    for (Condition cond : alarms.getOrDefault(oldPos, new HashSet<>()))
                                        cond.signalAll();
                                }
                                finally {
                                    alarmsLock.unlock();
                                }
                            System.out.println(frame.username + " is now at location " + pos);
                        }
                        else if (frame.tag == 3) {
                            System.out.println("Location probing request.");
                            Locations.Position pos = Locations.Position.fromByteArray(frame.data);
                            int numUsers = locations.usersAtPos(pos);
                            c.send(3, "", String.valueOf(numUsers).getBytes());
                        }
                        else if (frame.tag == 30) {
                            Locations.Position pos = Locations.Position.fromByteArray(frame.data);
                            new Thread(() -> {
                                Condition cond = alarmsLock.newCondition();
                                alarmsLock.lock();
                                try {
                                    alarms.computeIfAbsent(pos, (p) -> new HashSet<>()).add(cond);
                                    while (locations.usersAtPos(pos) > 0) {
                                        cond.await();
                                    }
                                    c.send(30, "", new byte[1]);
                                }
                                catch (Exception ignored) {

                                }
                                finally {
                                    alarmsLock.unlock();
                                }
                            }).start();
                        }
                    }
                } catch (IOException ignored) {

                }
            };
// Locations should have a Map that for each location (two coordinates, x and y) keeps a set of all the users (identified by their username) that are in that location.
            // It should also have a history Map, similar to the first one, but instead of storing the current location it stores all of a user's locations.
            for (int i = 0; i < WORKERS_PER_CONNECTION; ++i)
                new Thread(worker).start();
        }

    }
}
