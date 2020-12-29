import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

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

        while(true) {
            Socket s = ss.accept();
            Connection c = new Connection(s);

            Runnable worker = () -> {
                try (c) {
                    while(true) {
                        Frame frame = c.receive();
                        int tag = frame.tag;

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
                                    c.send(1, "", "Registration was successful!".getBytes());
                                }
                            } finally {
                                accounts.l.writeLock().unlock();
                            }
                            accounts.serialize("accounts.ser");
                        }
                    }
                } catch (Exception ignored) { }
            };

            for (int i = 0; i < WORKERS_PER_CONNECTION; ++i)
                new Thread(worker).start();
        }

    }
}
