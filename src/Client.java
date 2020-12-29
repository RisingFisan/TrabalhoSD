import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

public class Client {
    public static void main(String[] args) throws Exception {
        Socket s = new Socket("localhost", 12345);
        Demultiplexer m = new Demultiplexer(new Connection(s));
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

        m.close();
    }
}
