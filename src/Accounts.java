import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Accounts implements Serializable {
    private final HashMap<String, String> credentialsMap;
    public ReentrantReadWriteLock l = new ReentrantReadWriteLock();

    public Accounts() {
        this.credentialsMap = new HashMap<>();
    }

    public String getPassword(String email) {
        return credentialsMap.get(email);
    }

    public void addAccount(String email, String password) {
        credentialsMap.put(email, password);
    }

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

}
