import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.*;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.text.SimpleDateFormat;


public class WarehouseMonitoringSystem {

    private static final Logger LOGGER = Logger.getLogger(WarehouseMonitoringSystem.class.getName());
    private static final int DATA_RETENTION_DAYS = 30;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private DatabaseReference database;
    private Map<String, WarehouseConfig> warehouseConfigs;
    private Map<String, User> userCache;
    private ScheduledExecutorService scheduler;
    private ExecutorService taskExecutor;

    // Configuration
    private SystemConfiguration systemConfig;

    public WarehouseMonitoringSystem() throws IOException {
        initializeLogger();
        initializeFirebase();
        this.warehouseConfigs = new ConcurrentHashMap<>();
        this.userCache = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(4);
        this.taskExecutor = Executors.newFixedThreadPool(10);
        this.systemConfig = new SystemConfiguration();

        LOGGER.info("Warehouse Monitoring System initialized successfully");
    }

    /**
     * Initialize logging system
     */
    private void initializeLogger() {
        try {
            FileHandler fileHandler = new FileHandler("warehouse_system_%g.log", 10485760, 5, true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }

    /**
     * Initialize Firebase connection with retry mechanism
     */
    private void initializeFirebase() throws IOException {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RETRY_ATTEMPTS) {
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
                return;

            } catch (Exception e) {
                lastException = e;
                attempts++;
                LOGGER.warning("Firebase initialization attempt " + attempts + " failed: " + e.getMessage());

                if (attempts < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(2000 * attempts); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        throw new IOException("Failed to initialize Firebase after " + MAX_RETRY_ATTEMPTS + " attempts", lastException);
    }

    /**
     * Start monitoring all warehouses
     */
    public void startMonitoring() {
        safeOperation(() -> {
            // Load user cache
            loadUserCache();

            // Listen for warehouse configuration changes
            database.child("warehouses").addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    handleWarehouseAdded(snapshot);
                }

                @Override
                public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                    handleWarehouseUpdated(snapshot);
                }

                @Override
                public void onChildRemoved(DataSnapshot snapshot) {
                    handleWarehouseRemoved(snapshot);
                }

                @Override
                public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    LOGGER.severe("Error monitoring warehouses: " + error.getMessage());
                    logError("Warehouse monitoring error", error.toException());
                }
            });

            // Listen for sensor data
            database.child("sensorData").addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    String warehouseId = snapshot.getKey();
                    monitorWarehouseSensors(warehouseId);
                }

                @Override
                public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                }

                @Override
                public void onChildRemoved(DataSnapshot snapshot) {
                }

                @Override
                public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    LOGGER.warning("Sensor data monitoring error: " + error.getMessage());
                }
            });

            LOGGER.info("Monitoring system started successfully");

        }, "Failed to start monitoring system");
    }

    /**
     * Load user cache for permission checking and notifications
     */
    private void loadUserCache() {
    database.child("users").addValueEventListener(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot snapshot) {
            userCache.clear();
            for (DataSnapshot child : snapshot.getChildren()) {
                try {
                    // Load top-level user data
                    GenericTypeIndicator<Map<String, Object>> t = new GenericTypeIndicator<Map<String, Object>>() {};
                    Map<String, Object> userData = child.getValue(t);

                    if (userData != null) {
                        User user = new User();
                        user.setUid(child.getKey());
                        user.setEmail((String) userData.get("email"));
                        user.setName((String) userData.get("name"));
                        user.setRoleString((String) userData.get("role")); // Convert string to enum

                        Object isActiveObj = userData.get("isActive");
                        user.setActive(isActiveObj instanceof Boolean ? (Boolean) isActiveObj : true);

                        Object createdAtObj = userData.get("createdAt");
                        if (createdAtObj instanceof Number) {
                            user.setCreatedAt(((Number) createdAtObj).longValue());
                        }

                        Object lastLoginObj = userData.get("lastLogin");
                        if (lastLoginObj instanceof Number) {
                            user.setLastLogin(((Number) lastLoginObj).longValue());
                        }

                        // Load notification preferences safely
                        DataSnapshot prefsSnapshot = child.child("notificationPreferences");
                        GenericTypeIndicator<Map<String, Boolean>> tPrefs = new GenericTypeIndicator<Map<String, Boolean>>() {};
                        Map<String, Boolean> prefs = prefsSnapshot.getValue(tPrefs);
                        user.setNotificationPreferences(prefs != null ? prefs : new HashMap<>());

                        // Load assigned warehouses safely
                        DataSnapshot warehousesSnapshot = child.child("assignedWarehouses");
                        GenericTypeIndicator<List<String>> tWarehouses = new GenericTypeIndicator<List<String>>() {};
                        List<String> warehouses = warehousesSnapshot.getValue(tWarehouses);
                        user.setAssignedWarehouses(warehouses != null ? warehouses : new ArrayList<>());

                        userCache.put(child.getKey(), user);
                    }
                } catch (Exception e) {
                    LOGGER.warning("Failed to load user " + child.getKey() + ": " + e.getMessage());
                }
            }
            LOGGER.info("User cache loaded: " + userCache.size() + " users");
        }

        @Override
        public void onCancelled(DatabaseError error) {
            LOGGER.severe("Failed to load user cache: " + error.getMessage());
        }
    });
}

    /**
     * Handle warehouse added event
     */
    private void handleWarehouseAdded(DataSnapshot snapshot) {
        safeOperation(() -> {
            String warehouseId = snapshot.getKey();
            WarehouseConfig config = snapshot.getValue(WarehouseConfig.class);

            if (config == null) {
                LOGGER.warning("Null warehouse config for ID: " + warehouseId);
                return;
            }

            config.setId(warehouseId);

            // Validate warehouse configuration
            if (!validateWarehouseConfig(config)) {
                LOGGER.warning("Invalid warehouse configuration: " + warehouseId);
                return;
            }

            warehouseConfigs.put(warehouseId, config);
            LOGGER.info("Warehouse added: " + config.getName() + " (ID: " + warehouseId + ")");

            // Log audit trail
            logAuditEvent("WAREHOUSE_ADDED", warehouseId, config.getUserId(),
                    "Added warehouse: " + config.getName());

        }, "Error handling warehouse added event");
    }

    /**
     * Handle warehouse updated event
     */
    private void handleWarehouseUpdated(DataSnapshot snapshot) {
        safeOperation(() -> {
            String warehouseId = snapshot.getKey();
            WarehouseConfig config = snapshot.getValue(WarehouseConfig.class);

            if (config == null)
                return;

            config.setId(warehouseId);

            if (validateWarehouseConfig(config)) {
                WarehouseConfig oldConfig = warehouseConfigs.get(warehouseId);
                warehouseConfigs.put(warehouseId, config);
                LOGGER.info("Warehouse updated: " + config.getName());

                // Log changes
                if (oldConfig != null) {
                    logConfigurationChanges(warehouseId, oldConfig, config);
                }
            }

        }, "Error handling warehouse updated event");
    }

    /**
     * Handle warehouse removed event
     */
    private void handleWarehouseRemoved(DataSnapshot snapshot) {
        safeOperation(() -> {
            String warehouseId = snapshot.getKey();
            WarehouseConfig config = warehouseConfigs.remove(warehouseId);

            if (config != null) {
                LOGGER.info("Warehouse removed: " + config.getName() + " (ID: " + warehouseId + ")");
                logAuditEvent("WAREHOUSE_REMOVED", warehouseId, config.getUserId(),
                        "Removed warehouse: " + config.getName());
            }

        }, "Error handling warehouse removed event");
    }

    /**
     * Validate warehouse configuration
     */
    private boolean validateWarehouseConfig(WarehouseConfig config) {
        if (config == null)
            return false;

        if (config.getName() == null || config.getName().trim().isEmpty()) {
            LOGGER.warning("Warehouse name is empty");
            return false;
        }

        if (config.getMinTemp() >= config.getMaxTemp()) {
            LOGGER.warning("Invalid temperature range for warehouse: " + config.getName());
            return false;
        }

        if (config.getMinHumidity() >= config.getMaxHumidity()) {
            LOGGER.warning("Invalid humidity range for warehouse: " + config.getName());
            return false;
        }

        if (config.getMinTemp() < -50 || config.getMaxTemp() > 50) {
            LOGGER.warning("Temperature range out of realistic bounds for warehouse: " + config.getName());
            return false;
        }

        if (config.getMinHumidity() < 0 || config.getMaxHumidity() > 100) {
            LOGGER.warning("Humidity range out of valid bounds for warehouse: " + config.getName());
            return false;
        }

        return true;
    }

    /**
     * Monitor sensor data for a specific warehouse
     */
    private void monitorWarehouseSensors(String warehouseId) {
        database.child("sensorData").child(warehouseId)
                .limitToLast(1)
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                        taskExecutor.submit(() -> {
                            try {
                                SensorReading reading = snapshot.getValue(SensorReading.class);
                                if (reading != null && validateSensorReading(reading)) {
                                    processSensorReading(warehouseId, reading);
                                }
                            } catch (Exception e) {
                                LOGGER.severe("Error processing sensor reading: " + e.getMessage());
                                logError("Sensor processing error for warehouse: " + warehouseId, e);
                            }
                        });
                    }

                    @Override
                    public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                    }

                    @Override
                    public void onChildRemoved(DataSnapshot snapshot) {
                    }

                    @Override
                    public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        LOGGER.severe(
                                "Error monitoring sensor for warehouse " + warehouseId + ": " + error.getMessage());
                    }
                });
    }

    /**
     * Validate sensor reading
     */
    private boolean validateSensorReading(SensorReading reading) {
        if (reading == null)
            return false;

        // Check for realistic sensor values
        if (reading.getTemperature() < -60 || reading.getTemperature() > 60) {
            LOGGER.warning("Unrealistic temperature reading: " + reading.getTemperature());
            return false;
        }

        if (reading.getHumidity() < 0 || reading.getHumidity() > 100) {
            LOGGER.warning("Invalid humidity reading: " + reading.getHumidity());
            return false;
        }

        // Check timestamp is not in the future
        if (reading.getTimestamp() > System.currentTimeMillis() + 60000) {
            LOGGER.warning("Sensor reading has future timestamp");
            return false;
        }

        return true;
    }

    /**
     * Process incoming sensor reading with enhanced error handling
     */
    private void processSensorReading(String warehouseId, SensorReading reading) {
        WarehouseConfig config = warehouseConfigs.get(warehouseId);
        if (config == null) {
            LOGGER.warning("No config found for warehouse: " + warehouseId);
            return;
        }

        LOGGER.fine(String.format("Processing reading for %s: Temp=%.2f°C, Humidity=%.2f%%",
                config.getName(), reading.getTemperature(), reading.getHumidity()));

        // Check for alerts
        List<Alert> alerts = checkForAlerts(config, reading);

        // Determine alert severity
        String overallSeverity = determineOverallSeverity(alerts);

        if (!alerts.isEmpty()) {
            for (Alert alert : alerts) {
                sendAlert(warehouseId, config, alert);
            }

            // Update warehouse status
            safeAsyncOperation(
                    () -> database.child("warehouses").child(warehouseId).child("status").setValueAsync("alert"),
                    "Failed to update warehouse status to alert");
        } else {
            // Update to normal status
            safeAsyncOperation(
                    () -> database.child("warehouses").child(warehouseId).child("status").setValueAsync("normal"),
                    "Failed to update warehouse status to normal");
        }

        // Update last reading timestamp
        safeAsyncOperation(() -> database.child("warehouses").child(warehouseId).child("lastReading")
                .setValueAsync(reading.getTimestamp()),
                "Failed to update last reading timestamp");

        // Store analytics
        storeAnalytics(warehouseId, reading, !alerts.isEmpty(), overallSeverity);
    }

    /**
     * Determine overall severity from multiple alerts
     */
    private String determineOverallSeverity(List<Alert> alerts) {
        if (alerts.isEmpty())
            return "NONE";

        boolean hasCritical = alerts.stream().anyMatch(a -> "CRITICAL".equals(a.getSeverity()));
        boolean hasHigh = alerts.stream().anyMatch(a -> "HIGH".equals(a.getSeverity()));
        boolean hasWarning = alerts.stream().anyMatch(a -> "WARNING".equals(a.getSeverity()));

        if (hasCritical)
            return "CRITICAL";
        if (hasHigh)
            return "HIGH";
        if (hasWarning)
            return "WARNING";
        return "INFO";
    }

    /**
     * Check sensor readings against thresholds with severity levels
     */
    private List<Alert> checkForAlerts(WarehouseConfig config, SensorReading reading) {
        List<Alert> alerts = new ArrayList<>();

        double tempDeviation = 0;
        double humidityDeviation = 0;

        // Temperature check with severity levels
        if (reading.getTemperature() < config.getMinTemp()) {
            tempDeviation = config.getMinTemp() - reading.getTemperature();
            String severity = determineSeverity(tempDeviation, config.getMaxTemp() - config.getMinTemp());

            alerts.add(new Alert(
                    "TEMPERATURE_LOW",
                    String.format("Temperature too low: %.2f°C (Min: %.2f°C, Deviation: %.2f°C)",
                            reading.getTemperature(), config.getMinTemp(), tempDeviation),
                    severity,
                    reading.getTimestamp()));
        } else if (reading.getTemperature() > config.getMaxTemp()) {
            tempDeviation = reading.getTemperature() - config.getMaxTemp();
            String severity = determineSeverity(tempDeviation, config.getMaxTemp() - config.getMinTemp());

            alerts.add(new Alert(
                    "TEMPERATURE_HIGH",
                    String.format("Temperature too high: %.2f°C (Max: %.2f°C, Deviation: %.2f°C)",
                            reading.getTemperature(), config.getMaxTemp(), tempDeviation),
                    severity,
                    reading.getTimestamp()));
        }

        // Humidity check with severity levels
        if (reading.getHumidity() < config.getMinHumidity()) {
            humidityDeviation = config.getMinHumidity() - reading.getHumidity();
            String severity = determineSeverity(humidityDeviation, config.getMaxHumidity() - config.getMinHumidity());

            alerts.add(new Alert(
                    "HUMIDITY_LOW",
                    String.format("Humidity too low: %.2f%% (Min: %.2f%%, Deviation: %.2f%%)",
                            reading.getHumidity(), config.getMinHumidity(), humidityDeviation),
                    severity,
                    reading.getTimestamp()));
        } else if (reading.getHumidity() > config.getMaxHumidity()) {
            humidityDeviation = reading.getHumidity() - config.getMaxHumidity();
            String severity = determineSeverity(humidityDeviation, config.getMaxHumidity() - config.getMinHumidity());

            alerts.add(new Alert(
                    "HUMIDITY_HIGH",
                    String.format("Humidity too high: %.2f%% (Max: %.2f%%, Deviation: %.2f%%)",
                            reading.getHumidity(), config.getMaxHumidity(), humidityDeviation),
                    severity,
                    reading.getTimestamp()));
        }

        return alerts;
    }

    /**
     * Determine alert severity based on deviation
     */
    private String determineSeverity(double deviation, double allowedRange) {
        double deviationPercent = (deviation / allowedRange) * 100;

        if (deviationPercent > 50)
            return "CRITICAL";
        if (deviationPercent > 25)
            return "HIGH";
        if (deviationPercent > 10)
            return "WARNING";
        return "INFO";
    }

    /**
     * Send alert to Firebase with user notification preferences
     */
    private void sendAlert(String warehouseId, WarehouseConfig config, Alert alert) {
        safeOperation(() -> {
            LOGGER.warning(String.format("[%s] ALERT for %s: %s",
                    alert.getSeverity(), config.getName(), alert.getMessage()));

            Map<String, Object> alertData = new HashMap<>();
            alertData.put("type", alert.getType());
            alertData.put("message", alert.getMessage());
            alertData.put("severity", alert.getSeverity());
            alertData.put("timestamp", alert.getTimestamp());
            alertData.put("warehouseName", config.getName());
            alertData.put("warehouseId", warehouseId);
            alertData.put("acknowledged", false);
            alertData.put("acknowledgedBy", null);
            alertData.put("acknowledgedAt", null);

            safeAsyncOperation(() -> database.child("alerts").child(warehouseId).push().setValueAsync(alertData),
                    "Failed to store alert data");

            // Send notifications to relevant users
            sendUserNotifications(warehouseId, config, alert);

        }, "Error sending alert for warehouse: " + warehouseId);
    }

    /**
     * Send notifications to users based on preferences and permissions
     */
    private void sendUserNotifications(String warehouseId, WarehouseConfig config, Alert alert) {
        // Send to warehouse owner
        User owner = userCache.get(config.getUserId());
        if (owner != null && shouldNotifyUser(owner, alert)) {
            sendUserNotification(owner, config.getName(), alert);
        }

        // Send to other users with access to this warehouse
        for (User user : userCache.values()) {
            if (user.isActive() &&
                    !user.getUid().equals(config.getUserId()) &&
                    user.canAccessWarehouse(warehouseId) &&
                    shouldNotifyUser(user, alert)) {
                sendUserNotification(user, config.getName(), alert);
            }
        }
    }

    /**
     * Check if user should receive notification based on preferences
     */
    private boolean shouldNotifyUser(User user, Alert alert) {
        if (user == null || !user.isActive())
            return false;

        String severity = alert.getSeverity();

        if ("CRITICAL".equals(severity)) {
            return user.shouldReceiveNotification("critical_alerts");
        } else if ("HIGH".equals(severity) || "WARNING".equals(severity)) {
            return user.shouldReceiveNotification("warning_alerts");
        }

        return user.shouldReceiveNotification("email_alerts");
    }

    /**
     * Send notification to specific user
     */
    private void sendUserNotification(User user, String warehouseName, Alert alert) {
        safeOperation(() -> {
            Map<String, Object> notification = new HashMap<>();
            notification.put("title", "Warehouse Alert: " + warehouseName);
            notification.put("message", alert.getMessage());
            notification.put("severity", alert.getSeverity());
            notification.put("timestamp", alert.getTimestamp());
            notification.put("read", false);
            notification.put("type", alert.getType());

            safeAsyncOperation(
                    () -> database.child("notifications").child(user.getUid()).push().setValueAsync(notification),
                    "Failed to send notification to user: " + user.getUid());

            LOGGER.info("Notification sent to " + user.getName() + " for " + warehouseName);

        }, "Error sending notification to user: " + user.getUid());
    }

    /**
     * Store analytics data with enhanced metrics
     */
    private void storeAnalytics(String warehouseId, SensorReading reading, boolean hasAlert, String severity) {
        safeOperation(() -> {
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("warehouseId", warehouseId);
            analytics.put("temperature", reading.getTemperature());
            analytics.put("humidity", reading.getHumidity());
            analytics.put("timestamp", reading.getTimestamp());
            analytics.put("hasAlert", hasAlert);
            analytics.put("severity", severity);

            // Add hour for hourly analytics
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(reading.getTimestamp());
            analytics.put("hour", cal.get(Calendar.HOUR_OF_DAY));
            analytics.put("dayOfWeek", cal.get(Calendar.DAY_OF_WEEK));

            // Store by date for easy querying
            String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(reading.getTimestamp()));

            safeAsyncOperation(
                    () -> database.child("analytics").child(date).child(warehouseId).push().setValueAsync(analytics),
                    "Failed to store analytics data");

        }, "Error storing analytics for warehouse: " + warehouseId);
    }

    /**
     * Log configuration changes for audit trail
     */
    private void logConfigurationChanges(String warehouseId, WarehouseConfig oldConfig, WarehouseConfig newConfig) {
        List<String> changes = new ArrayList<>();

        if (!oldConfig.getName().equals(newConfig.getName())) {
            changes.add("Name changed from '" + oldConfig.getName() + "' to '" + newConfig.getName() + "'");
        }
        if (oldConfig.getMinTemp() != newConfig.getMinTemp()) {
            changes.add("Min temperature changed from " + oldConfig.getMinTemp() + " to " + newConfig.getMinTemp());
        }
        if (oldConfig.getMaxTemp() != newConfig.getMaxTemp()) {
            changes.add("Max temperature changed from " + oldConfig.getMaxTemp() + " to " + newConfig.getMaxTemp());
        }
        if (oldConfig.getMinHumidity() != newConfig.getMinHumidity()) {
            changes.add(
                    "Min humidity changed from " + oldConfig.getMinHumidity() + " to " + newConfig.getMinHumidity());
        }
        if (oldConfig.getMaxHumidity() != newConfig.getMaxHumidity()) {
            changes.add(
                    "Max humidity changed from " + oldConfig.getMaxHumidity() + " to " + newConfig.getMaxHumidity());
        }

        if (!changes.isEmpty()) {
            String changeLog = String.join("; ", changes);
            LOGGER.info("Warehouse configuration changes for " + warehouseId + ": " + changeLog);
            logAuditEvent("WAREHOUSE_MODIFIED", warehouseId, newConfig.getUserId(), changeLog);
        }
    }

    /**
     * Log audit event to Firebase
     */
    private void logAuditEvent(String eventType, String warehouseId, String userId, String description) {
        safeAsyncOperation(() -> {
            Map<String, Object> auditLog = new HashMap<>();
            auditLog.put("eventType", eventType);
            auditLog.put("warehouseId", warehouseId);
            auditLog.put("userId", userId);
            auditLog.put("description", description);
            auditLog.put("timestamp", System.currentTimeMillis());

            database.child("auditLogs").push().setValueAsync(auditLog);
        }, "Failed to log audit event");
    }

    /**
     * Log error to Firebase for tracking
     */
    private void logError(String message, Exception exception) {
        safeAsyncOperation(() -> {
            Map<String, Object> errorLog = new HashMap<>();
            errorLog.put("message", message);
            errorLog.put("exception", exception.getClass().getName());
            errorLog.put("exceptionMessage", exception.getMessage());
            errorLog.put("timestamp", System.currentTimeMillis());
            errorLog.put("stackTrace", Arrays.toString(exception.getStackTrace()).substring(0,
                    Math.min(1000, Arrays.toString(exception.getStackTrace()).length())));

            database.child("errorLogs").push().setValueAsync(errorLog);
        }, "Failed to log error");
    }

    /**
     * Simulate sensor data for testing
     */
    public void startSensorSimulation() {
        scheduler.scheduleAtFixedRate(() -> {
            safeOperation(() -> {
                for (Map.Entry<String, WarehouseConfig> entry : warehouseConfigs.entrySet()) {
                    String warehouseId = entry.getKey();
                    WarehouseConfig config = entry.getValue();

                    // Generate realistic sensor data
                    SensorReading reading = generateSensorData(config);

                    // Store in Firebase
                    safeAsyncOperation(
                            () -> database.child("sensorData").child(warehouseId).push().setValueAsync(reading),
                            "Failed to store simulated sensor data for warehouse: " + warehouseId);
                }
            }, "Error in sensor simulation");
        }, 0, systemConfig.getSensorSimulationInterval(), TimeUnit.SECONDS);

        LOGGER.info("Sensor simulation started (" + systemConfig.getSensorSimulationInterval() + " second intervals)");
    }

    /**
     * Generate simulated sensor data with more realistic patterns
     */
    private SensorReading generateSensorData(WarehouseConfig config) {
        Random random = new Random();

        // 85% chance of normal reading, 15% chance of alert
        boolean isNormal = random.nextDouble() > 0.15;

        double temperature, humidity;

        if (isNormal) {
            // Generate values within range with slight randomness
            double tempRange = config.getMaxTemp() - config.getMinTemp();
            double tempCenter = config.getMinTemp() + (tempRange / 2);
            temperature = tempCenter + (random.nextGaussian() * tempRange * 0.2);

            // Clamp to valid range
            temperature = Math.max(config.getMinTemp(), Math.min(config.getMaxTemp(), temperature));

            double humidityRange = config.getMaxHumidity() - config.getMinHumidity();
            double humidityCenter = config.getMinHumidity() + (humidityRange / 2);
            humidity = humidityCenter + (random.nextGaussian() * humidityRange * 0.2);

            humidity = Math.max(config.getMinHumidity(), Math.min(config.getMaxHumidity(), humidity));
        } else {
            // Generate out-of-range values to trigger alerts
            if (random.nextBoolean()) {
                // Temperature alert
                temperature = random.nextBoolean() ? config.getMaxTemp() + (random.nextDouble() * 5)
                        : config.getMinTemp() - (random.nextDouble() * 5);
                humidity = config.getMinHumidity() +
                        (random.nextDouble() * (config.getMaxHumidity() - config.getMinHumidity()));
            } else {
                // Humidity alert
                temperature = config.getMinTemp() +
                        (random.nextDouble() * (config.getMaxTemp() - config.getMinTemp()));
                humidity = random.nextBoolean() ? config.getMaxHumidity() + (random.nextDouble() * 20)
                        : config.getMinHumidity() - (random.nextDouble() * 10);
            }
        }

        return new SensorReading(
                Math.round(temperature * 100.0) / 100.0,
                Math.round(humidity * 100.0) / 100.0,
                System.currentTimeMillis());
    }

    /**
     * Generate daily reports
     */
    public void generateDailyReport() {
        scheduler.scheduleAtFixedRate(() -> {
            safeOperation(() -> {
                String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

                LOGGER.info("\n=== Daily Report for " + date + " ===");

                for (Map.Entry<String, WarehouseConfig> entry : warehouseConfigs.entrySet()) {
                    String warehouseId = entry.getKey();
                    WarehouseConfig config = entry.getValue();

                    generateWarehouseReport(warehouseId, config, date);
                }

                LOGGER.info("=================================\n");

            }, "Error generating daily report");
        }, calculateInitialDelay(), 24, TimeUnit.HOURS);
    }

    /**
     * Calculate delay until next midnight
     */
    private long calculateInitialDelay() {
        Calendar now = Calendar.getInstance();
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        midnight.add(Calendar.DAY_OF_MONTH, 1);

        return midnight.getTimeInMillis() - now.getTimeInMillis();
    }

    /**
     * Generate report for a specific warehouse
     */
    private void generateWarehouseReport(String warehouseId, WarehouseConfig config, String date) {
    database.child("analytics").child(date).child(warehouseId)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (!snapshot.exists()) {
                        LOGGER.info("No data available for warehouse: " + config.getName());
                        return;
                    }

                    List<Double> temperatures = new ArrayList<>();
                    List<Double> humidities = new ArrayList<>();
                    int alertCount = 0;
                    int criticalAlertCount = 0;

                    GenericTypeIndicator<Map<String, Object>> t = new GenericTypeIndicator<Map<String, Object>>() {};

                    for (DataSnapshot child : snapshot.getChildren()) {
                        Map<String, Object> data = child.getValue(t); // type-safe
                        if (data != null) {
                            Object tempObj = data.get("temperature");
                            Object humidityObj = data.get("humidity");

                            if (tempObj instanceof Number) {
                                temperatures.add(((Number) tempObj).doubleValue());
                            }
                            if (humidityObj instanceof Number) {
                                humidities.add(((Number) humidityObj).doubleValue());
                            }

                            if (Boolean.TRUE.equals(data.get("hasAlert"))) {
                                alertCount++;
                                if ("CRITICAL".equals(data.get("severity"))) {
                                    criticalAlertCount++;
                                }
                            }
                        }
                    }

                    if (!temperatures.isEmpty()) {
                        double avgTemp = temperatures.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                        double minTemp = temperatures.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                        double maxTemp = temperatures.stream().mapToDouble(Double::doubleValue).max().orElse(0);

                        double avgHumidity = humidities.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                        double minHumidity = humidities.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                        double maxHumidity = humidities.stream().mapToDouble(Double::doubleValue).max().orElse(0);

                        StringBuilder report = new StringBuilder();
                        report.append("Warehouse: ").append(config.getName()).append("\n");
                        report.append("  Temperature - Avg: ").append(String.format("%.2f°C", avgTemp))
                                .append(", Min: ").append(String.format("%.2f°C", minTemp))
                                .append(", Max: ").append(String.format("%.2f°C", maxTemp)).append("\n");
                        report.append("  Humidity - Avg: ").append(String.format("%.2f%%", avgHumidity))
                                .append(", Min: ").append(String.format("%.2f%%", minHumidity))
                                .append(", Max: ").append(String.format("%.2f%%", maxHumidity)).append("\n");
                        report.append("  Total Readings: ").append(temperatures.size()).append("\n");
                        report.append("  Alert Count: ").append(alertCount);
                        if (criticalAlertCount > 0) {
                            report.append(" (").append(criticalAlertCount).append(" critical)");
                        }
                        report.append("\n");

                        LOGGER.info(report.toString());

                        // Store report in database
                        storeReport(warehouseId, date, avgTemp, avgHumidity,
                                temperatures.size(), alertCount, criticalAlertCount);
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    LOGGER.severe("Error generating report for " + config.getName() + ": " + error.getMessage());
                }
            });
}
    /**
     * Store generated report in database
     */
    private void storeReport(String warehouseId, String date, double avgTemp,
            double avgHumidity, int readingCount, int alertCount, int criticalCount) {
        safeAsyncOperation(() -> {
            Map<String, Object> report = new HashMap<>();
            report.put("warehouseId", warehouseId);
            report.put("date", date);
            report.put("avgTemperature", avgTemp);
            report.put("avgHumidity", avgHumidity);
            report.put("readingCount", readingCount);
            report.put("alertCount", alertCount);
            report.put("criticalAlertCount", criticalCount);
            report.put("generatedAt", System.currentTimeMillis());

            database.child("reports").child(date).child(warehouseId).setValueAsync(report);
        }, "Failed to store report");
    }

    /**
     * Cleanup old data to prevent database bloat
     */
    public void scheduleDataCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            safeOperation(() -> {
                long cutoffTime = System.currentTimeMillis() -
                        (DATA_RETENTION_DAYS * 24L * 60 * 60 * 1000);
                String cutoffDate = new SimpleDateFormat("yyyy-MM-dd")
                        .format(new Date(cutoffTime));

                LOGGER.info("Starting data cleanup for dates before: " + cutoffDate);

                // Cleanup analytics data
                cleanupAnalyticsData(cutoffDate);

                // Cleanup old alerts
                cleanupOldAlerts(cutoffTime);

                // Cleanup old notifications
                cleanupOldNotifications(cutoffTime);

                LOGGER.info("Data cleanup completed");

            }, "Error during data cleanup");
        }, 1, 7, TimeUnit.DAYS); // Run weekly

        LOGGER.info("Data cleanup scheduled (every 7 days, retention: " + DATA_RETENTION_DAYS + " days)");
    }

    /**
     * Cleanup old analytics data
     */
    private void cleanupAnalyticsData(String cutoffDate) {
        database.child("analytics").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                int deletedDates = 0;
                for (DataSnapshot dateSnapshot : snapshot.getChildren()) {
                    String date = dateSnapshot.getKey();
                    if (date != null && date.compareTo(cutoffDate) < 0) {
                        dateSnapshot.getRef().removeValue((error, ref) -> {
                            if (error != null) {
                                LOGGER.warning("Failed to delete analytics date " + date + ": " + error.getMessage());
                            }
                        });
                        deletedDates++;
                    }
                }
                final int finalCount = deletedDates;
                LOGGER.info("Cleaned up " + finalCount + " days of analytics data");
            }

            @Override
            public void onCancelled(DatabaseError error) {
                LOGGER.warning("Failed to cleanup analytics: " + error.getMessage());
            }
        });
    }

    /**
     * Cleanup old alerts
     */
    private void cleanupOldAlerts(long cutoffTime) {
    database.child("alerts").addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot snapshot) {
            int deletedAlerts = 0;

            GenericTypeIndicator<Map<String, Object>> tAlert = new GenericTypeIndicator<Map<String, Object>>() {};

            for (DataSnapshot warehouseSnapshot : snapshot.getChildren()) {
                for (DataSnapshot alertSnapshot : warehouseSnapshot.getChildren()) {
                    Map<String, Object> alert = alertSnapshot.getValue(tAlert); // type-safe
                    if (alert != null) {
                        Object timestampObj = alert.get("timestamp");
                        if (timestampObj instanceof Number) {
                            long timestamp = ((Number) timestampObj).longValue();
                            if (timestamp < cutoffTime) {
                                alertSnapshot.getRef().removeValue((error, ref) -> {
                                    if (error != null) {
                                        LOGGER.warning("Failed to delete alert: " + error.getMessage());
                                    }
                                });
                                deletedAlerts++;
                            }
                        }
                    }
                }
            }

            LOGGER.info("Cleaned up " + deletedAlerts + " old alerts");
        }

        @Override
        public void onCancelled(DatabaseError error) {
            LOGGER.warning("Failed to cleanup alerts: " + error.getMessage());
        }
    });
}
    /**
     * Cleanup old notifications
     */
    private void cleanupOldNotifications(long cutoffTime) {
    database.child("notifications").addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot snapshot) {
            int deletedNotifications = 0;

            GenericTypeIndicator<Map<String, Object>> tNotification = new GenericTypeIndicator<Map<String, Object>>() {};

            for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                for (DataSnapshot notificationSnapshot : userSnapshot.getChildren()) {
                    Map<String, Object> notification = notificationSnapshot.getValue(tNotification); // type-safe
                    if (notification != null) {
                        Object timestampObj = notification.get("timestamp");
                        Object readObj = notification.get("read");
                        Boolean read = readObj instanceof Boolean ? (Boolean) readObj : false;

                        if (timestampObj instanceof Number && Boolean.TRUE.equals(read)) {
                            long timestamp = ((Number) timestampObj).longValue();
                            if (timestamp < cutoffTime) {
                                notificationSnapshot.getRef().removeValue((error, ref) -> {
                                    if (error != null) {
                                        LOGGER.warning("Failed to delete notification: " + error.getMessage());
                                    }
                                });
                                deletedNotifications++;
                            }
                        }
                    }
                }
            }

            LOGGER.info("Cleaned up " + deletedNotifications + " old notifications");
        }

        @Override
        public void onCancelled(DatabaseError error) {
            LOGGER.warning("Failed to cleanup notifications: " + error.getMessage());
        }
    });
}

    /**
     * Safe operation wrapper with error handling
     */
    private void safeOperation(Runnable operation, String errorMessage) {
        try {
            operation.run();
        } catch (Exception e) {
            LOGGER.severe(errorMessage + ": " + e.getMessage());
            e.printStackTrace();
            logError(errorMessage, e);
        }
    }

    /**
     * Safe async operation wrapper
     */
    private void safeAsyncOperation(Runnable operation, String errorMessage) {
        try {
            operation.run();
        } catch (Exception e) {
            LOGGER.warning(errorMessage + ": " + e.getMessage());
        }
    }

    /**
     * Get system health status
     */
    public Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("warehouses", warehouseConfigs.size());
        health.put("activeUsers", userCache.size());
        health.put("uptime", System.currentTimeMillis());
        health.put("timestamp", System.currentTimeMillis());

        // Count users by role
        long adminCount = userCache.values().stream()
                .filter(u -> u.getRole() != null && u.getRole().getValue().equals("admin"))
                .count();
        long operatorCount = userCache.values().stream()
                .filter(u -> u.getRole() != null && u.getRole().getValue().equals("operator"))
                .count();
        long viewerCount = userCache.values().stream()
                .filter(u -> u.getRole() != null && u.getRole().getValue().equals("viewer"))
                .count();

        health.put("adminUsers", adminCount);
        health.put("operatorUsers", operatorCount);
        health.put("viewerUsers", viewerCount);

        return health;
    }

    /**
     * Ensure at least one admin exists
     */
    private void ensureAdminExists() {
        safeOperation(() -> {
            database.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    boolean hasAdmin = false;

                    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                        Map<String, Object> userData = (Map<String, Object>) userSnapshot.getValue();
                        if (userData != null && "admin".equals(userData.get("role"))) {
                            hasAdmin = true;
                            break;
                        }
                    }

                    if (!hasAdmin) {
                        LOGGER.warning("========================================");
                        LOGGER.warning("WARNING: No admin user found in system!");
                        LOGGER.warning("Please run AdminInitializer to create an admin user");
                        LOGGER.warning("Command: java AdminInitializer <email> <password> <name>");
                        LOGGER.warning("========================================");
                    } else {
                        LOGGER.info("Admin user verified in system");
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    LOGGER.warning("Could not verify admin user: " + error.getMessage());
                }
            });
        }, "Error checking for admin user");
    }

    /**
     * Shutdown the system gracefully
     */
    public void shutdown() {
        LOGGER.info("Initiating system shutdown...");

        // Shutdown schedulers
        scheduler.shutdown();
        taskExecutor.shutdown();

        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!taskExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                taskExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            taskExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("System shutdown complete");
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        try {
            WarehouseMonitoringSystem system = new WarehouseMonitoringSystem();

            // Start all services
            system.startMonitoring();
            system.startSensorSimulation();
            system.generateDailyReport();
            system.scheduleDataCleanup();

            LOGGER.info("========================================");
            LOGGER.info("Warehouse Monitoring System is running");
            LOGGER.info("========================================");
            LOGGER.info("Press Ctrl+C to stop");

            // Add shutdown hook for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutdown signal received");
                system.shutdown();
            }));

            // Keep the application running
            Thread.currentThread().join();

        } catch (Exception e) {
            LOGGER.severe("Fatal error starting system: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

/**
 * System Configuration Class
 */
class SystemConfiguration {
    private int sensorSimulationInterval = 30; // seconds
    private int maxRetryAttempts = 3;
    private int dataRetentionDays = 30;

    public int getSensorSimulationInterval() {
        return sensorSimulationInterval;
    }

    public void setSensorSimulationInterval(int interval) {
        this.sensorSimulationInterval = interval;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(int attempts) {
        this.maxRetryAttempts = attempts;
    }

    public int getDataRetentionDays() {
        return dataRetentionDays;
    }

    public void setDataRetentionDays(int days) {
        this.dataRetentionDays = days;
    }
}

/**
 * Enhanced Warehouse Configuration Model
 */
class WarehouseConfig {
    private String id;
    private String name;
    private String location;
    private String type;
    private double minTemp;
    private double maxTemp;
    private double minHumidity;
    private double maxHumidity;
    private String userId;
    private String status;
    private long createdAt;
    private String createdBy;
    private long lastModified;
    private String lastModifiedBy;
    private long lastReading;

    public WarehouseConfig() {
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getMinTemp() {
        return minTemp;
    }

    public void setMinTemp(double minTemp) {
        this.minTemp = minTemp;
    }

    public double getMaxTemp() {
        return maxTemp;
    }

    public void setMaxTemp(double maxTemp) {
        this.maxTemp = maxTemp;
    }

    public double getMinHumidity() {
        return minHumidity;
    }

    public void setMinHumidity(double minHumidity) {
        this.minHumidity = minHumidity;
    }

    public double getMaxHumidity() {
        return maxHumidity;
    }

    public void setMaxHumidity(double maxHumidity) {
        this.maxHumidity = maxHumidity;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public long getLastReading() {
        return lastReading;
    }

    public void setLastReading(long lastReading) {
        this.lastReading = lastReading;
    }
}

/**
 * Sensor Reading Model
 */
class SensorReading {
    private double temperature;
    private double humidity;
    private long timestamp;
    private String sensorId;
    private String batteryLevel;

    public SensorReading() {
    }

    public SensorReading(double temperature, double humidity, long timestamp) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.timestamp = timestamp;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getHumidity() {
        return humidity;
    }

    public void setHumidity(double humidity) {
        this.humidity = humidity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public String getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(String batteryLevel) {
        this.batteryLevel = batteryLevel;
    }
}

/**
 * Alert Model with Enhanced Fields
 */
class Alert {
    private String type;
    private String message;
    private String severity;
    private long timestamp;
    private boolean acknowledged;
    private String acknowledgedBy;
    private long acknowledgedAt;

    public Alert() {
    }

    public Alert(String type, String message, String severity, long timestamp) {
        this.type = type;
        this.message = message;
        this.severity = severity;
        this.timestamp = timestamp;
        this.acknowledged = false;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    public String getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public void setAcknowledgedBy(String acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }

    public long getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(long acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }
}

// Note: User class is defined in separate User.java file