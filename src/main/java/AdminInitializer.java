import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.*;
import com.google.firebase.database.*;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Admin User Initializer
 * Creates the first admin user in the system
 */
public class AdminInitializer {
    
    private DatabaseReference database;
    private FirebaseAuth auth;
    
    public AdminInitializer() throws IOException {
        initializeFirebase();
    }
    
    /**
     * Initialize Firebase
     */
    private void initializeFirebase() throws IOException {
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
        this.auth = FirebaseAuth.getInstance();
        
        System.out.println("Firebase initialized successfully");
    }
    
    /**
     * Create admin user
     */
    public void createAdminUser(String email, String password, String name) {
        try {
            System.out.println("Creating admin user: " + email);
            
            // Create user in Firebase Authentication
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                .setEmail(email)
                .setPassword(password)
                .setDisplayName(name)
                .setEmailVerified(true);
            
            UserRecord userRecord = auth.createUser(request);
            String uid = userRecord.getUid();
            
            System.out.println("User created in Authentication with UID: " + uid);
            
            // Create user profile in database with ADMIN role
            Map<String, Object> userData = new HashMap<>();
            userData.put("email", email);
            userData.put("name", name);
            userData.put("role", "admin"); // Admin role
            userData.put("createdAt", System.currentTimeMillis());
            userData.put("lastLogin", System.currentTimeMillis());
            userData.put("isActive", true);
            
            // Initialize notification preferences
            Map<String, Boolean> notificationPrefs = new HashMap<>();
            notificationPrefs.put("email_alerts", true);
            notificationPrefs.put("critical_alerts", true);
            notificationPrefs.put("warning_alerts", true);
            notificationPrefs.put("daily_reports", true);
            notificationPrefs.put("weekly_summary", true);
            userData.put("notificationPreferences", notificationPrefs);
            
            // Empty warehouse assignments (admin has access to all)
            userData.put("assignedWarehouses", new ArrayList<>());
            
            // Wait for database write to complete
            CountDownLatch latch = new CountDownLatch(1);
            database.child("users").child(uid).setValue(userData, (error, ref) -> {
                if (error != null) {
                    System.err.println("Error saving user data: " + error.getMessage());
                } else {
                    System.out.println("Admin user profile created successfully!");
                    System.out.println("UID: " + uid);
                    System.out.println("Email: " + email);
                    System.out.println("Role: ADMIN");
                }
                latch.countDown();
            });
            
            latch.await(10, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            System.err.println("Error creating admin user: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * List all users in the system
     */
   public void listAllUsers() {
    try {
        System.out.println("\n=== All Users in System ===");

        CountDownLatch latch = new CountDownLatch(1);

        GenericTypeIndicator<Map<String, Object>> tUser = new GenericTypeIndicator<Map<String, Object>>() {};

        database.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    System.out.println("No users found in database");
                    latch.countDown();
                    return;
                }

                int count = 0;
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    count++;
                    String uid = userSnapshot.getKey();
                    Map<String, Object> userData = userSnapshot.getValue(tUser); // type-safe

                    if (userData != null) {
                        System.out.println("\n--- User " + count + " ---");
                        System.out.println("UID: " + uid);
                        System.out.println("Name: " + userData.get("name"));
                        System.out.println("Email: " + userData.get("email"));
                        System.out.println("Role: " + userData.get("role"));
                        System.out.println("Active: " + userData.get("isActive"));

                        Object createdAtObj = userData.get("createdAt");
                        if (createdAtObj instanceof Number) {
                            long createdAt = ((Number) createdAtObj).longValue();
                            System.out.println("Created: " + new Date(createdAt));
                        }
                    }
                }

                System.out.println("\nTotal users: " + count);
                latch.countDown();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("Error listing users: " + error.getMessage());
                latch.countDown();
            }
        });

        latch.await(10, TimeUnit.SECONDS);

    } catch (Exception e) {
        System.err.println("Error listing users: " + e.getMessage());
        e.printStackTrace();
    }
}

    
    /**
     * Update user role
     */
    public void updateUserRole(String email, String newRole) {
        try {
            System.out.println("Updating role for user: " + email);
            
            // Get user by email
            UserRecord userRecord = auth.getUserByEmail(email);
            String uid = userRecord.getUid();
            
            CountDownLatch latch = new CountDownLatch(1);
            
            database.child("users").child(uid).child("role").setValue(newRole, (error, ref) -> {
                if (error != null) {
                    System.err.println("Error updating role: " + error.getMessage());
                } else {
                    System.out.println("Role updated successfully!");
                    System.out.println("User: " + email);
                    System.out.println("New Role: " + newRole);
                }
                latch.countDown();
            });
            
            latch.await(10, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            System.err.println("Error updating user role: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Delete user
     */
    public void deleteUser(String email) {
        try {
            System.out.println("Deleting user: " + email);
            
            // Get user by email
            UserRecord userRecord = auth.getUserByEmail(email);
            String uid = userRecord.getUid();
            
            // Delete from authentication
            auth.deleteUser(uid);
            
            // Delete from database
            CountDownLatch latch = new CountDownLatch(1);
            
            database.child("users").child(uid).removeValue((error, ref) -> {
                if (error != null) {
                    System.err.println("Error deleting user from database: " + error.getMessage());
                } else {
                    System.out.println("User deleted successfully!");
                }
                latch.countDown();
            });
            
            latch.await(10, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            System.err.println("Error deleting user: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Check if any admin exists
     */
    public boolean hasAdminUser() {
    try {
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] hasAdmin = {false};

        GenericTypeIndicator<Map<String, Object>> tUser = new GenericTypeIndicator<Map<String, Object>>() {};

        database.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    Map<String, Object> userData = userSnapshot.getValue(tUser); // type-safe
                    if (userData != null && "admin".equals(userData.get("role"))) {
                        hasAdmin[0] = true;
                        break;
                    }
                }
                latch.countDown();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                latch.countDown();
            }
        });

        latch.await(10, TimeUnit.SECONDS);
        return hasAdmin[0];

    } catch (Exception e) {
        System.err.println("Error checking for admin: " + e.getMessage());
        return false;
    }
}

    /**
     * Interactive menu
     */
    public void showMenu() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        
        while (running) {
            System.out.println("\n========================================");
            System.out.println("    ADMIN USER MANAGEMENT");
            System.out.println("========================================");
            System.out.println("1. Create Admin User");
            System.out.println("2. Create Operator User");
            System.out.println("3. Create Viewer User");
            System.out.println("4. List All Users");
            System.out.println("5. Update User Role");
            System.out.println("6. Delete User");
            System.out.println("7. Check for Admin");
            System.out.println("0. Exit");
            System.out.println("========================================");
            System.out.print("Select option: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    createUserInteractive(scanner, "admin");
                    break;
                case "2":
                    createUserInteractive(scanner, "operator");
                    break;
                case "3":
                    createUserInteractive(scanner, "viewer");
                    break;
                case "4":
                    listAllUsers();
                    break;
                case "5":
                    updateRoleInteractive(scanner);
                    break;
                case "6":
                    deleteUserInteractive(scanner);
                    break;
                case "7":
                    if (hasAdminUser()) {
                        System.out.println("✓ Admin user exists in the system");
                    } else {
                        System.out.println("✗ No admin user found. Please create one.");
                    }
                    break;
                case "0":
                    running = false;
                    System.out.println("Exiting...");
                    break;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
        
        scanner.close();
    }
    
    private void createUserInteractive(Scanner scanner, String role) {
        System.out.print("\nEnter name: ");
        String name = scanner.nextLine().trim();
        
        System.out.print("Enter email: ");
        String email = scanner.nextLine().trim();
        
        System.out.print("Enter password (min 6 characters): ");
        String password = scanner.nextLine().trim();
        
        if (name.isEmpty() || email.isEmpty() || password.length() < 6) {
            System.out.println("Invalid input. Name and email required, password must be at least 6 characters.");
            return;
        }
        
        createAdminUser(email, password, name);
    }
    
    private void updateRoleInteractive(Scanner scanner) {
        System.out.print("\nEnter user email: ");
        String email = scanner.nextLine().trim();
        
        System.out.println("Select new role:");
        System.out.println("1. admin");
        System.out.println("2. operator");
        System.out.println("3. viewer");
        System.out.print("Choice: ");
        String roleChoice = scanner.nextLine().trim();
        
        String role;
        switch (roleChoice) {
            case "1": role = "admin"; break;
            case "2": role = "operator"; break;
            case "3": role = "viewer"; break;
            default:
                System.out.println("Invalid role choice");
                return;
        }
        
        updateUserRole(email, role);
    }
    
    private void deleteUserInteractive(Scanner scanner) {
        System.out.print("\nEnter user email to delete: ");
        String email = scanner.nextLine().trim();
        
        System.out.print("Are you sure? (yes/no): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        
        if (confirm.equals("yes")) {
            deleteUser(email);
        } else {
            System.out.println("Deletion cancelled");
        }
    }
    
    /**
     * Main method
     */
    public static void main(String[] args) {
        try {
            AdminInitializer initializer = new AdminInitializer();
            
            // Check if arguments provided for quick admin creation
            if (args.length == 3) {
                // Command line: java AdminInitializer <email> <password> <name>
                String email = args[0];
                String password = args[1];
                String name = args[2];
                
                System.out.println("Creating admin user from command line arguments...");
                initializer.createAdminUser(email, password, name);
                
            } else if (args.length == 1 && args[0].equals("--list")) {
                // List all users
                initializer.listAllUsers();
                
            } else {
                // Show interactive menu
                initializer.showMenu();
            }
            
            System.exit(0);
            
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}