import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.*;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.logging.Logger;


public class WarehouseRMIServer {
    
    private static final Logger LOGGER = Logger.getLogger(WarehouseRMIServer.class.getName());
    private static final int RMI_PORT = 1099;
    private static final String SERVICE_NAME = "WarehouseMonitoring";
    
    private DatabaseReference database;
    private Registry registry;
    
    public WarehouseRMIServer() throws IOException {
        initializeFirebase();
    }
    
    /**
     * Initialize Firebase connection
     */
    private void initializeFirebase() throws IOException {
        try {
            java.io.InputStream serviceAccount = getClass().getClassLoader()
                    .getResourceAsStream("serviceAccountKey.json");
            
            if (serviceAccount == null) {
                throw new IOException("Service account key not found in resources!");
            }
            
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://warehouses-f4498-default-rtdb.europe-west1.firebasedatabase.app")
                    .build();
            
            FirebaseApp.initializeApp(options);
            this.database = FirebaseDatabase.getInstance().getReference();
            
            LOGGER.info("Firebase initialized successfully");
            
        } catch (Exception e) {
            throw new IOException("Failed to initialize Firebase", e);
        }
    }
    
    /**
     * Start RMI server
     */
    public void start() {
        try {
            // Create RMI registry
            LOGGER.info("Creating RMI registry on port " + RMI_PORT);
            registry = LocateRegistry.createRegistry(RMI_PORT);
            
            // Create remote object
            WarehouseMonitoringRemote monitoringService = new WarehouseMonitoringImpl(database);
            
            // Bind remote object to registry
            registry.rebind(SERVICE_NAME, monitoringService);
            
            LOGGER.info("========================================");
            LOGGER.info("RMI Server started successfully!");
            LOGGER.info("Service name: " + SERVICE_NAME);
            LOGGER.info("Port: " + RMI_PORT);
            LOGGER.info("========================================");
            LOGGER.info("Clients can connect using:");
            LOGGER.info("  rmi://localhost:" + RMI_PORT + "/" + SERVICE_NAME);
            LOGGER.info("========================================");
            
        } catch (Exception e) {
            LOGGER.severe("Failed to start RMI server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Stop RMI server
     */
    public void stop() {
        try {
            if (registry != null) {
                registry.unbind(SERVICE_NAME);
                LOGGER.info("RMI server stopped");
            }
        } catch (Exception e) {
            LOGGER.warning("Error stopping RMI server: " + e.getMessage());
        }
    }
    
    /**
     * Main method
     */
    public static void main(String[] args) {
        try {
            WarehouseRMIServer server = new WarehouseRMIServer();
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutting down RMI server...");
                server.stop();
            }));
            
            // Start server
            server.start();
            
            // Keep server running
            Thread.currentThread().join();
            
        } catch (Exception e) {
            LOGGER.severe("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

