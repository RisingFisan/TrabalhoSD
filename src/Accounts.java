import java.io.*;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Allows us to efficiently store and access users' credentials.
 */
public class Accounts implements Serializable {
    private final HashMap<String, String> credentialsMap;
    public ReentrantReadWriteLock l = new ReentrantReadWriteLock();

    public Accounts() {
        this.credentialsMap = new HashMap<>();
    }

    /**
     * Fetches a user's password.
     * @param email the user's email address.
     * @return the user's password, or <i>null</i> if the user is not registered in the system.
     */
    public String getPassword(String email) {
        return credentialsMap.get(email);
    }

    /**
     * Adds a new account to the system with the specified credentials.
     * @param email the user's email address.
     * @param password the user's password.
     */
    public void addAccount(String email, String password) {
        credentialsMap.put(email, password);
    }

    /**
     * Checks if an account is registered in the system.
     * @param email the email address to check.
     * @return <i>true</i> if an account with the specified email exists. <i>false</i> otherwise.
     */
    public boolean accountExists(String email) {
        return credentialsMap.containsKey(email);
    }

    public void serialize(String filepath) throws IOException {
        FileOutputStream fos = new FileOutputStream(filepath);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(this);
        oos.close();
        fos.close();
    }

    public static Accounts deserialize(String filepath) throws IOException, ClassNotFoundException {
        FileInputStream fis = new FileInputStream(filepath);
        ObjectInputStream ois = new ObjectInputStream(fis);
        Accounts accounts = (Accounts) ois.readObject();
        ois.close();
        fis.close();
        return accounts;
    }
}
