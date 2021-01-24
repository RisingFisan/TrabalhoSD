import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Client-side implementation of the program.
 *
 * Instance of <code>Server</code> must be running before running instance of <code>Client</code>.
 * Multiple instances of this class can run at the same time.
 */
public class Client {
    public static void main(String[] args) throws Exception {
        Socket s = new Socket("localhost", 12345);
        Demultiplexer m = new Demultiplexer(new Connection(s));

        HashSet<Thread> alarms = new HashSet<>();

        m.start();

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        String username = null;

        while (username == null) {
            System.out.print("***COVIDON'T***\n"
                           + "\n"
                           + "O que pretende fazer?\n"
                           + "1) Iniciar sessão.\n"
                           + "2) Registar nova conta.\n"
                           + "\n"
                           + "Insira o valor corresponde à operação desejada: ");
            String option = stdin.readLine();
            if(option.equals("1")) {
                System.out.print("***INICIAR SESSÃO***\n"
                                + "\n"
                                + "Introduza o seu endereço de email: ");
                String email = stdin.readLine();
                System.out.print("Introduza a sua palavra-passe: ");
                String password = stdin.readLine();
                m.send(0, email, password.getBytes());
                String response = new String(m.receive(0));
                if(!response.startsWith("Erro")) {
                    username = email;
                }
                System.out.println("\n" + response + "\n");
            }
            else if (option.equals("2")) {
                System.out.print("***REGISTAR NOVA CONTA***\n"
                        + "\n"
                        + "Introduza o seu endereço de email: ");
                String email = stdin.readLine();
                System.out.print("Introduza a sua palavra-passe: ");
                String password = stdin.readLine();
                m.send(1, email, password.getBytes());
                String response = new String(m.receive(1));
                if(!response.startsWith("Erro")) {
                    username = email;
                }
                System.out.println("\n" + response + "\n");
            }
        }

        while (true) {
            System.out.print("***COVIDON'T***\n"
                    + "\n"
                    + "Introduza a sua localização atual, no formato \"(x,y)\": ");
            String location = stdin.readLine().strip();
            try {
                Locations.Position pos = new Locations.Position(location);
                m.send(2, username, String.format("%d %d", pos.x, pos.y).getBytes());
                break;
            }
            catch (IllegalStateException e) {
                System.out.println("\nErro - localização inválida - tente novamente.");
            }
        }

        final String finalUsername = username;
        Thread sickThread = new Thread(() -> {
            try {
                byte[] r = m.receive(112);
                if(r.length == 1)
                    System.out.println("\n***AVISO: ESTEVE EM CONTACTO COM UMA PESSOA INFETADA***\n");
            }
            catch (Exception ignored) { }
        });
        sickThread.start();

        boolean exit = false;
        while (!exit) {
            System.out.print("\n***COVIDON'T***\n"
                    + "\n"
                    + "O que pretende fazer?\n"
                    + "1) Deslocar-me.\n"
                    + "2) Ver ocupação de uma dada localização.\n"
                    + "3) Indicar que estou doente.\n"
                    + (username.startsWith("admin") ? "4) Obter mapa de utilizadores por localização.\n" : "")
                    + "\n"
                    + "0) Sair.\n"
                    + "\n"
                    + "Insira o valor corresponde à operação desejada: ");
            String option = stdin.readLine();
            switch(option) {
                case "0":
                    m.send(99, username, new byte[0]);
                    exit = true;
                    break;
                case "1":
                    while (true) {
                        System.out.print("***DESLOCAÇÃO***\n"
                                + "\n"
                                + "Indique a sua nova localização, no formato \"(x,y)\": ");
                        String location = stdin.readLine();
                        try {
                            Locations.Position pos = new Locations.Position(location);
                            m.send(2, username, String.format("%d %d", pos.x, pos.y).getBytes());
                            break;
                        }
                        catch (IllegalStateException e) {
                            System.out.println("\n" + e + "\n");
                        }
                    }
                    break;
                case "2":
                    System.out.print("***OCUPAÇÃO DE UMA LOCALIZAÇÃO***\n"
                            + "\n"
                            + "Indique a localização para a qual pretende saber a ocupação, no formato \"(x,y)\": ");
                    String location = stdin.readLine();
                    try {
                        Locations.Position pos = new Locations.Position(location);
                        m.send(3, username, pos.toByteArray());
                        int response = Integer.parseInt(new String(m.receive(3)));
                        System.out.println("\nEstão " + response + " pessoas nesta localização.\n");
                        if (response > 0) {
                            System.out.print("Pretende receber uma notificação quando esta localização estiver vazia? [s/N] ");
                            String getAlarm = stdin.readLine();
                            if (getAlarm.equalsIgnoreCase("s") || getAlarm.equalsIgnoreCase("y")) {
                                Thread alarm = new Thread(() -> {
                                    try {
                                        m.send(30, finalUsername, pos.toByteArray());
                                        byte[] r = m.receive(30);
                                        if(r.length == 1)
                                            System.out.println("\n***A LOCALIZAÇÃO " + pos + " ENCONTRA-SE LIVRE***\n");
                                    }
                                    catch (Exception ignored) { }
                                });
                                alarms.add(alarm);
                                alarm.start();
                            }
                        }
                    }
                    catch (IllegalStateException e) {
                        System.out.println("\n" + e + "\n");
                    }
                    break;
                case "3":
                    m.send(99, username, new byte[1]);
                    exit = true;
                    break;
                case "4":
                    if (username.startsWith("admin")) {
                        m.send(10, username, new byte[0]);
                        String map = new String(m.receive(10));
                        System.out.println(map);
                    }
            }
        }

        for(Thread t : alarms)
            t.join();
        sickThread.join();

        m.close();
    }
}
