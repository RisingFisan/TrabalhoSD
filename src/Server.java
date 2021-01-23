import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/** Classe relativa ao servidor do programa **/
public class Server {

    public static void main(String[] args) throws Exception {
        ServerSocket ss = new ServerSocket(12345); //socket tcp

        final Accounts accounts;
        final Locations locations;

        /** Serialização das contas e localizações**/
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

        ReentrantLock liuLock = new ReentrantLock();
        HashSet<String> loggedInUsers = new HashSet<>();

        ReentrantLock sickLock = new ReentrantLock();
        Condition sickCondition = sickLock.newCondition();
        HashSet<String> sickUsers = new HashSet<>();

        while(true) {
            Socket s = ss.accept();
            Connection c = new Connection(s);

            Runnable worker = () -> {
                try (c) {
                    while(true) {
                        boolean loggedIn = false;
                        Frame frame = c.receive();

                        if (frame.tag == 0) {
                            System.out.println("User log-in attempt.");
                            String email = frame.username;
                            String password = new String(frame.data);
                            String stored_password;
                            accounts.l.readLock().lock();
                            try {
                                stored_password = accounts.getPassword(email);
                            } finally {
                                accounts.l.readLock().unlock();
                            }
                            if (stored_password != null) {
                                if (stored_password.equals(password)) {
                                    c.send(0, "", "Sessão iniciada com sucesso!".getBytes());
                                    loggedIn = true;
                                    liuLock.lock();
                                    try { loggedInUsers.add(frame.username); }
                                    finally { liuLock.unlock(); }
                                }
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
                                    loggedIn = true;
                                    liuLock.lock();
                                    try { loggedInUsers.add(frame.username); }
                                    finally { liuLock.unlock(); }
                                }
                            } finally {
                                accounts.l.writeLock().unlock();
                            }
                        }
                        //Mudar localização
                        else if (frame.tag == 2) {
                            System.out.println("User location update.");
                            String[] coordinates = new String(frame.data).split(" ");
                            Locations.Position pos = new Locations.Position(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]));

                            locations.l.writeLock().lock();
                            try {
                                Locations.Position oldPos = locations.moveUser(frame.username, pos);
                                if (locations.usersAtPos(oldPos) == 0) {
                                    alarmsLock.lock();
                                    try {
                                        alarms.getOrDefault(oldPos, new HashSet<>())
                                                .stream()
                                                .map(AbstractMap.SimpleEntry::getValue)
                                                .forEach(Condition::signalAll);
                                    } finally {
                                        alarmsLock.unlock();
                                    }
                                }
                                locations.serialize("locations.ser");
                            }
                            finally {
                                locations.l.writeLock().unlock();
                            }
                            System.out.println(frame.username + " is now at location " + pos);
                        }
                        //Saber ocupação de localização
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

                        //Pedido de mapa de localizações
                        else if (frame.tag == 10) {
                            System.out.println("Location map request.");
                            Map<Locations.Position, Set<String>> locationHistory;
                            try {
                                locations.l.readLock().lock();
                                locationHistory = locations.getHistory();
                            } finally { locations.l.readLock().unlock(); }
                            System.out.println(locationHistory.toString());
                            StringBuilder sb = new StringBuilder();
                            for (Locations.Position pos : locationHistory.keySet()) {
                                sb.append(String.format("%s: %d utilizadores estiveram aqui, %d dos quais doentes.\n",pos.toString(),locationHistory.get(pos).size(),
                                        locationHistory.get(pos).stream().filter(sickUsers::contains).count()));
                            }
                            c.send(10,"", sb.toString().getBytes());
                        }

                        //Localização livre
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
                                        if (! alarms.containsKey(pos) || ! loggedInUsers.contains(frame.username)) {
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
                        //sair
                        else if (frame.tag == 99) {

                            liuLock.lock();
                            try {
                                loggedInUsers.remove(frame.username);
                            }
                            finally {
                                liuLock.unlock();
                            }

                            sickLock.lock();
                            try {
                                if (frame.data.length > 0)
                                    sickUsers.add(frame.username);
                                sickCondition.signalAll();
                            }
                            finally {
                                sickLock.unlock();
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

                        if (loggedIn) {
                            try { sickLock.lock();
                                sickUsers.remove(frame.username);
                            } finally { sickLock.unlock(); }

                            new Thread(() -> {
                                sickLock.lock();
                                try {
                                    while (true) {
                                        liuLock.lock();
                                        try {
                                            if (!loggedInUsers.contains(frame.username)) {
                                                c.send(112, "", new byte[0]);
                                                break;
                                            }
                                        }
                                        finally {
                                            liuLock.unlock();
                                        }
                                        boolean sick = false;
                                        for (String user : sickUsers) {
                                            if (!user.equals(frame.username) && locations.wereInContact(user, frame.username)) {
                                                sick = true;
                                                break;
                                            }
                                        }
                                        if (sick) {
                                            c.send(112, "", new byte[1]);
                                            break;
                                        }
                                        sickCondition.await();
                                    }
                                }
                                catch (Exception ignored) { }
                                finally {
                                    sickLock.unlock();
                                }
                            }).start();
                        }
                    }
                } catch (IOException ignored) {

                }
            };

            new Thread(worker).start();
        }
    }
}
