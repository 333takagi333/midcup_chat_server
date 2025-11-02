import com.chat.server.Server;
import com.chat.utils.DatabaseManager;

public class Main {
    public static void main(String[] args) {
        // Add a shutdown hook to gracefully close the database connection pool
        Runtime.getRuntime().addShutdownHook(new Thread(DatabaseManager::shutdown));

        // Force database connection pool initialization before starting the server
        System.out.println("Initializing database connection pool...");
        try (var connection = DatabaseManager.getConnection()) {
            System.out.println("Database connection pool initialized successfully.");
        } catch (Exception e) {
            System.err.println("Failed to initialize database connection pool. Exiting.");
            e.printStackTrace();
            return; // Exit if DB connection fails
        }

        Server server = new Server(12345);
        server.start();
    }
}
