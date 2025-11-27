import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

public class WarehouseRMIClient {
    
    private WarehouseMonitoringRemote service;
    private String userId;
    
    /**
     * Connect to RMI server
     */
    public void connect(String host, int port) throws Exception {
        System.out.println("Connecting to RMI server at " + host + ":" + port);
        
        Registry registry = LocateRegistry.getRegistry(host, port);
        service = (WarehouseMonitoringRemote) registry.lookup("WarehouseMonitoring");
        
        // Test connection
        long serverTime = service.ping();
        System.out.println("✓ Connected successfully! Server time: " + new Date(serverTime));
    }
    
    /**
     * Set current user ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    /**
     * List all warehouses
     */
    public void listWarehouses() throws Exception {
        System.out.println("\n=== Warehouses ===");
        Map<String, Map<String, Object>> warehouses = service.getWarehouses(userId);
        
        if (warehouses.isEmpty()) {
            System.out.println("No warehouses found.");
            return;
        }
        
        for (Map.Entry<String, Map<String, Object>> entry : warehouses.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> warehouse = entry.getValue();
            
            System.out.println("\nWarehouse ID: " + id);
            System.out.println("  Name: " + warehouse.get("name"));
            System.out.println("  Location: " + warehouse.get("location"));
            System.out.println("  Type: " + warehouse.get("type"));
            System.out.println("  Status: " + warehouse.get("status"));
            System.out.println("  Temp Range: " + warehouse.get("minTemp") + "°C - " + warehouse.get("maxTemp") + "°C");
            System.out.println("  Humidity Range: " + warehouse.get("minHumidity") + "% - " + warehouse.get("maxHumidity") + "%");
        }
    }
    
    /**
     * Get warehouse details
     */
    public void getWarehouseDetails(String warehouseId) throws Exception {
        System.out.println("\n=== Warehouse Details ===");
        Map<String, Object> warehouse = service.getWarehouse(warehouseId);
        
        if (warehouse == null) {
            System.out.println("Warehouse not found!");
            return;
        }
        
        System.out.println("Name: " + warehouse.get("name"));
        System.out.println("Location: " + warehouse.get("location"));
        System.out.println("Type: " + warehouse.get("type"));
        System.out.println("Status: " + warehouse.get("status"));
        
        // Get latest reading
        Map<String, Object> reading = service.getLatestReading(warehouseId);
        if (reading != null) {
            System.out.println("\n=== Latest Reading ===");
            System.out.println("Temperature: " + reading.get("temperature") + "°C");
            System.out.println("Humidity: " + reading.get("humidity") + "%");
            System.out.println("Timestamp: " + new Date((Long) reading.get("timestamp")));
        }
    }
    
    /**
     * Add new warehouse
     */
    public String addWarehouse(String name, String location, String type,
                               double minTemp, double maxTemp,
                               double minHumidity, double maxHumidity) throws Exception {
        System.out.println("\n=== Adding Warehouse ===");
        
        Map<String, Object> warehouseData = new HashMap<>();
        warehouseData.put("name", name);
        warehouseData.put("location", location);
        warehouseData.put("type", type);
        warehouseData.put("minTemp", minTemp);
        warehouseData.put("maxTemp", maxTemp);
        warehouseData.put("minHumidity", minHumidity);
        warehouseData.put("maxHumidity", maxHumidity);
        
        String warehouseId = service.addWarehouse(userId, warehouseData);
        System.out.println("✓ Warehouse added successfully!");
        System.out.println("Warehouse ID: " + warehouseId);
        
        return warehouseId;
    }
    
    /**
     * Add sensor reading
     */
    public void addSensorReading(String warehouseId, double temperature, double humidity) throws Exception {
        System.out.println("\n=== Adding Sensor Reading ===");
        
        boolean success = service.addSensorReading(warehouseId, temperature, humidity);
        
        if (success) {
            System.out.println("✓ Sensor reading added successfully!");
            System.out.println("Temperature: " + temperature + "°C");
            System.out.println("Humidity: " + humidity + "%");
        } else {
            System.out.println("✗ Failed to add sensor reading");
        }
    }
    
    /**
     * Get sensor history
     */
    public void getSensorHistory(String warehouseId, int limit) throws Exception {
        System.out.println("\n=== Sensor History (Last " + limit + " readings) ===");
        
        List<Map<String, Object>> history = service.getSensorHistory(warehouseId, limit);
        
        for (Map<String, Object> reading : history) {
            Date timestamp = new Date((Long) reading.get("timestamp"));
            System.out.printf("%s - Temp: %.2f°C, Humidity: %.2f%%\n",
                    timestamp, reading.get("temperature"), reading.get("humidity"));
        }
    }
    
    /**
     * Get active alerts
     */
    public void getAlerts(String warehouseId) throws Exception {
        System.out.println("\n=== Active Alerts ===");
        
        List<Map<String, Object>> alerts = service.getAlerts(warehouseId);
        
        if (alerts.isEmpty()) {
            System.out.println("No active alerts.");
            return;
        }
        
        for (Map<String, Object> alert : alerts) {
            System.out.println("\n" + alert.get("severity") + " - " + alert.get("type"));
            System.out.println("Message: " + alert.get("message"));
            System.out.println("Time: " + new Date((Long) alert.get("timestamp")));
            System.out.println("Acknowledged: " + alert.get("acknowledged"));
        }
    }
    
    /**
     * Get system health
     */
    public void getSystemHealth() throws Exception {
        System.out.println("\n=== System Health ===");
        
        Map<String, Object> health = service.getSystemHealth();
        
        System.out.println("Status: " + health.get("status"));
        System.out.println("Total Warehouses: " + health.get("warehouses"));
        System.out.println("Timestamp: " + new Date((Long) health.get("timestamp")));
    }
    
    /**
     * Get daily report
     */
    public void getDailyReport(String warehouseId, String date) throws Exception {
        System.out.println("\n=== Daily Report for " + date + " ===");
        
        Map<String, Object> report = service.getDailyReport(warehouseId, date);
        
        if (report == null) {
            System.out.println("No report available for this date.");
            return;
        }
        
        System.out.println("Average Temperature: " + report.get("avgTemperature") + "°C");
        System.out.println("Average Humidity: " + report.get("avgHumidity") + "%");
        System.out.println("Total Readings: " + report.get("readingCount"));
        System.out.println("Alert Count: " + report.get("alertCount"));
        System.out.println("Critical Alerts: " + report.get("criticalAlertCount"));
    }
    
    /**
     * Interactive menu
     */
    public void showMenu() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        
        while (running) {
            System.out.println("\n========================================");
            System.out.println("  WAREHOUSE MONITORING - RMI CLIENT");
            System.out.println("========================================");
            System.out.println("1.  List Warehouses");
            System.out.println("2.  Get Warehouse Details");
            System.out.println("3.  Add Warehouse");
            System.out.println("4.  Update Warehouse");
            System.out.println("5.  Delete Warehouse");
            System.out.println("6.  Get Latest Reading");
            System.out.println("7.  Get Sensor History");
            System.out.println("8.  Add Sensor Reading");
            System.out.println("9.  Get Alerts");
            System.out.println("10. Acknowledge Alert");
            System.out.println("11. Get Daily Report");
            System.out.println("12. Get System Health");
            System.out.println("0.  Exit");
            System.out.println("========================================");
            System.out.print("Select option: ");
            
            try {
                String choice = scanner.nextLine().trim();
                
                switch (choice) {
                    case "1":
                        listWarehouses();
                        break;
                    case "2":
                        System.out.print("Enter warehouse ID: ");
                        String detailId = scanner.nextLine();
                        getWarehouseDetails(detailId);
                        break;
                    case "3":
                        addWarehouseInteractive(scanner);
                        break;
                    case "4":
                        updateWarehouseInteractive(scanner);
                        break;
                    case "5":
                        deleteWarehouseInteractive(scanner);
                        break;
                    case "6":
                        System.out.print("Enter warehouse ID: ");
                        String readingId = scanner.nextLine();
                        Map<String, Object> latest = service.getLatestReading(readingId);
                        if (latest != null) {
                            System.out.println("Temperature: " + latest.get("temperature") + "°C");
                            System.out.println("Humidity: " + latest.get("humidity") + "%");
                        }
                        break;
                    case "7":
                        System.out.print("Enter warehouse ID: ");
                        String historyId = scanner.nextLine();
                        System.out.print("Enter limit (default 10): ");
                        int limit = Integer.parseInt(scanner.nextLine().trim());
                        getSensorHistory(historyId, limit);
                        break;
                    case "8":
                        addSensorReadingInteractive(scanner);
                        break;
                    case "9":
                        System.out.print("Enter warehouse ID: ");
                        String alertId = scanner.nextLine();
                        getAlerts(alertId);
                        break;
                    case "10":
                        acknowledgeAlertInteractive(scanner);
                        break;
                    case "11":
                        getDailyReportInteractive(scanner);
                        break;
                    case "12":
                        getSystemHealth();
                        break;
                    case "0":
                        running = false;
                        System.out.println("Goodbye!");
                        break;
                    default:
                        System.out.println("Invalid option");
                }
                
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        scanner.close();
    }
    
    private void addWarehouseInteractive(Scanner scanner) throws Exception {
        System.out.print("Name: ");
        String name = scanner.nextLine();
        System.out.print("Location: ");
        String location = scanner.nextLine();
        System.out.print("Type (Frozen/Vegetables/Meat/Dairy/Mixed): ");
        String type = scanner.nextLine();
        System.out.print("Min Temperature (°C): ");
        double minTemp = Double.parseDouble(scanner.nextLine());
        System.out.print("Max Temperature (°C): ");
        double maxTemp = Double.parseDouble(scanner.nextLine());
        System.out.print("Min Humidity (%): ");
        double minHumidity = Double.parseDouble(scanner.nextLine());
        System.out.print("Max Humidity (%): ");
        double maxHumidity = Double.parseDouble(scanner.nextLine());
        
        addWarehouse(name, location, type, minTemp, maxTemp, minHumidity, maxHumidity);
    }
    
    private void updateWarehouseInteractive(Scanner scanner) throws Exception {
        System.out.print("Enter warehouse ID: ");
        String warehouseId = scanner.nextLine();
        System.out.print("Enter field to update (name/location/minTemp/maxTemp/etc): ");
        String field = scanner.nextLine();
        System.out.print("Enter new value: ");
        String value = scanner.nextLine();
        
        Map<String, Object> updates = new HashMap<>();
        updates.put(field, value);
        
        boolean success = service.updateWarehouse(warehouseId, userId, updates);
        System.out.println(success ? "✓ Updated successfully!" : "✗ Update failed");
    }
    
    private void deleteWarehouseInteractive(Scanner scanner) throws Exception {
        System.out.print("Enter warehouse ID: ");
        String warehouseId = scanner.nextLine();
        System.out.print("Are you sure? (yes/no): ");
        String confirm = scanner.nextLine();
        
        if (confirm.equalsIgnoreCase("yes")) {
            boolean success = service.deleteWarehouse(warehouseId, userId);
            System.out.println(success ? "✓ Deleted successfully!" : "✗ Delete failed");
        }
    }
    
    private void addSensorReadingInteractive(Scanner scanner) throws Exception {
        System.out.print("Enter warehouse ID: ");
        String warehouseId = scanner.nextLine();
        System.out.print("Temperature (°C): ");
        double temp = Double.parseDouble(scanner.nextLine());
        System.out.print("Humidity (%): ");
        double humidity = Double.parseDouble(scanner.nextLine());
        
        addSensorReading(warehouseId, temp, humidity);
    }
    
    private void acknowledgeAlertInteractive(Scanner scanner) throws Exception {
        System.out.print("Enter warehouse ID: ");
        String warehouseId = scanner.nextLine();
        System.out.print("Enter alert ID: ");
        String alertId = scanner.nextLine();
        
        boolean success = service.acknowledgeAlert(warehouseId, alertId, userId);
        System.out.println(success ? "✓ Alert acknowledged!" : "✗ Failed to acknowledge");
    }
    
    private void getDailyReportInteractive(Scanner scanner) throws Exception {
        System.out.print("Enter warehouse ID: ");
        String warehouseId = scanner.nextLine();
        System.out.print("Enter date (yyyy-MM-dd): ");
        String date = scanner.nextLine();
        
        getDailyReport(warehouseId, date);
    }
    
    /**
     * Main method
     */
    public static void main(String[] args) {
        try {
            WarehouseRMIClient client = new WarehouseRMIClient();
            
            // Parse command line arguments
            String host = args.length > 0 ? args[0] : "localhost";
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 1099;
            String userId = args.length > 2 ? args[2] : "user123";
            
            // Connect to server
            client.connect(host, port);
            client.setUserId(userId);
            
            // Show interactive menu
            client.showMenu();
            
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}