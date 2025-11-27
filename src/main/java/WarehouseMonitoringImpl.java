
import com.google.firebase.database.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
//import java.text.SimpleDateFormat;

/**
 * Implementation of the Remote Warehouse Monitoring Interface
 */
public class WarehouseMonitoringImpl extends UnicastRemoteObject implements WarehouseMonitoringRemote {
    
    private static final long serialVersionUID = 1L;
    private final DatabaseReference database;
    private final Map<String, WarehouseConfig> warehouseCache;
    
    public WarehouseMonitoringImpl(DatabaseReference database) throws RemoteException {
        super();
        this.database = database;
        this.warehouseCache = new ConcurrentHashMap<>();
        loadWarehouseCache();
    }
    
    /**
     * Load warehouses into cache
     */
    private void loadWarehouseCache() {
        database.child("warehouses").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                warehouseCache.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    WarehouseConfig config = child.getValue(WarehouseConfig.class);
                    if (config != null) {
                        config.setId(child.getKey());
                        warehouseCache.put(child.getKey(), config);
                    }
                }
            }
            
            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("Error loading warehouse cache: " + error.getMessage());
            }
        });
    }
    
    // ========== Warehouse Operations ==========
    
    @Override
    public Map<String, Map<String, Object>> getWarehouses(String userId) throws RemoteException {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Map<String, Map<String, Object>> result = new HashMap<>();
            
            database.child("warehouses").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Map<String, Object> warehouse = (Map<String, Object>) child.getValue();
                        if (warehouse != null) {
                            // Check if user has access (owner or admin)
                            String ownerId = (String) warehouse.get("userId");
                            if (userId == null || userId.equals(ownerId)) {
                                result.put(child.getKey(), warehouse);
                            }
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
            return result;
            
        } catch (Exception e) {
            throw new RemoteException("Error getting warehouses", e);
        }
    }
    
    @Override
    public Map<String, Object> getWarehouse(String warehouseId) throws RemoteException {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Map<String, Object>[] result = new Map[1];
            
            database.child("warehouses").child(warehouseId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    result[0] = (Map<String, Object>) snapshot.getValue();
                    latch.countDown();
                }
                
                @Override
                public void onCancelled(DatabaseError error) {
                    latch.countDown();
                }
            });
            
            latch.await(10, TimeUnit.SECONDS);
            return result[0];
            
        } catch (Exception e) {
            throw new RemoteException("Error getting warehouse", e);
        }
    }
    
    @Override
    public String addWarehouse(String userId, Map<String, Object> warehouseData) throws RemoteException {
        try {
            // Validate required fields
            if (!warehouseData.containsKey("name") || !warehouseData.containsKey("location")) {
                throw new RemoteException("Missing required fields: name, location");
            }
            
            // Add metadata
            warehouseData.put("userId", userId);
            warehouseData.put("createdAt", System.currentTimeMillis());
            warehouseData.put("lastModified", System.currentTimeMillis());
            warehouseData.put("status", "normal");
            
            // Add to database
            CountDownLatch latch = new CountDownLatch(1);
            String[] warehouseId = new String[1];
            
            DatabaseReference newRef = database.child("warehouses").push();
            warehouseId[0] = newRef.getKey();
            
            newRef.setValue(warehouseData, (error, ref) -> {
                latch.countDown();
            });
            
            latch.await(10, TimeUnit.SECONDS);
            
            // Add initial sensor reading
            Map<String, Object> initialReading = new HashMap<>();
            initialReading.put("temperature", warehouseData.getOrDefault("minTemp", 0));
            initialReading.put("humidity", warehouseData.getOrDefault("minHumidity", 50));
            initialReading.put("timestamp", System.currentTimeMillis());
            
            database.child("sensorData").child(warehouseId[0]).push().setValue(initialReading, null);
            
            return warehouseId[0];
            
        } catch (Exception e) {
            throw new RemoteException("Error adding warehouse", e);
        }
    }
    
    @Override
    public boolean updateWarehouse(String warehouseId, String userId, Map<String, Object> updates) throws RemoteException {
        try {
            // Check permissions
            WarehouseConfig config = warehouseCache.get(warehouseId);
            if (config == null) {
                throw new RemoteException("Warehouse not found");
            }
            
            // Add last modified timestamp
            updates.put("lastModified", System.currentTimeMillis());
            updates.put("lastModifiedBy", userId);
            
            CountDownLatch latch = new CountDownLatch(1);
            boolean[] success = new boolean[1];
            
            database.child("warehouses").child(warehouseId).updateChildren(updates, (error, ref) -> {
                success[0] = (error == null);
                latch.countDown();
            });
            
            latch.await(10, TimeUnit.SECONDS);
            return success[0];
            
        } catch (Exception e) {
            throw new RemoteException("Error updating warehouse", e);
        }
    }
    
    @Override
    public boolean deleteWarehouse(String warehouseId, String userId) throws RemoteException {
        try {
            CountDownLatch latch = new CountDownLatch(3);
            
            // Delete warehouse
            database.child("warehouses").child(warehouseId).removeValue((error, ref) -> latch.countDown());
            
            // Delete sensor data
            database.child("sensorData").child(warehouseId).removeValue((error, ref) -> latch.countDown());
            
            // Delete alerts
            database.child("alerts").child(warehouseId).removeValue((error, ref) -> latch.countDown());
            
            latch.await(10, TimeUnit.SECONDS);
            return true;
            
        } catch (Exception e) {
            throw new RemoteException("Error deleting warehouse", e);
        }
    }
    
    // ========== Sensor Data Operations ==========
    
    @Override
    public Map<String, Object> getLatestReading(String warehouseId) throws RemoteException {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Map<String, Object>[] result = new Map[1];
            
            database.child("sensorData").child(warehouseId).limitToLast(1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            result[0] = (Map<String, Object>) child.getValue();
                        }
                        latch.countDown();
                    }
                    
                    @Override
                    public void onCancelled(DatabaseError error) {
                        latch.countDown();
                    }
                });
            
            latch.await(10, TimeUnit.SECONDS);
            return result[0];
            
        } catch (Exception e) {
            throw new RemoteException("Error getting latest reading", e);
        }
    }
    
    @Override
    public List<Map<String, Object>> getSensorHistory(String warehouseId, int limit) throws RemoteException {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            List<Map<String, Object>> result = new ArrayList<>();
            
            database.child("sensorData").child(warehouseId).limitToLast(limit)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Map<String, Object> reading = (Map<String, Object>) child.getValue();
                            if (reading != null) {
                                reading.put("id", child.getKey());
                                result.add(reading);
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
            return result;
            
        } catch (Exception e) {
            throw new RemoteException("Error getting sensor history", e);
        }
    }
    
    @Override
    public boolean addSensorReading(String warehouseId, double temperature, double humidity) throws RemoteException {
        try {
            Map<String, Object> reading = new HashMap<>();
            reading.put("temperature", temperature);
            reading.put("humidity", humidity);
            reading.put("timestamp", System.currentTimeMillis());
            
            CountDownLatch latch = new CountDownLatch(1);
            boolean[] success = new boolean[1];
            
            database.child("sensorData").child(warehouseId).push().setValue(reading, (error, ref) -> {
                success[0] = (error == null);
                latch.countDown();
            });
            
            latch.await(10, TimeUnit.SECONDS);
            return success[0];
            
        } catch (Exception e) {
            throw new RemoteException("Error adding sensor reading", e);
        }
    }
    
    // ========== Alert Operations ==========
    
    @Override
    public List<Map<String, Object>> getAlerts(String warehouseId) throws RemoteException {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            List<Map<String, Object>> result = new ArrayList<>();
            
            database.child("alerts").child(warehouseId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Map<String, Object> alert = (Map<String, Object>) child.getValue();
                            if (alert != null) {
                                alert.put("id", child.getKey());
                                result.add(alert);
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
            return result;
            
        } catch (Exception e) {
            throw new RemoteException("Error getting alerts", e);
        }
    }
    
    @Override
    public List<Map<String, Object>> getAllAlerts(String userId) throws RemoteException {
        try {
            List<Map<String, Object>> allAlerts = new ArrayList<>();
            Map<String, Map<String, Object>> warehouses = getWarehouses(userId);
            
            for (String warehouseId : warehouses.keySet()) {
                List<Map<String, Object>> warehouseAlerts = getAlerts(warehouseId);
                allAlerts.addAll(warehouseAlerts);
            }
            
            return allAlerts;
            
        } catch (Exception e) {
            throw new RemoteException("Error getting all alerts", e);
        }
    }
    
    @Override
    public boolean acknowledgeAlert(String warehouseId, String alertId, String userId) throws RemoteException {
        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("acknowledged", true);
            updates.put("acknowledgedBy", userId);
            updates.put("acknowledgedAt", System.currentTimeMillis());
            
            CountDownLatch latch = new CountDownLatch(1);
            boolean[] success = new boolean[1];
            
            database.child("alerts").child(warehouseId).child(alertId)
                .updateChildren(updates, (error, ref) -> {
                    success[0] = (error == null);
                    latch.countDown();
                });
            
            latch.await(10, TimeUnit.SECONDS);
            return success[0];
            
        } catch (Exception e) {
            throw new RemoteException("Error acknowledging alert", e);
        }
    }
    
    // ========== User Operations ==========
    
    @Override
    public Map<String, Object> getUser(String userId) throws RemoteException {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Map<String, Object>[] result = new Map[1];
            
            database.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    result[0] = (Map<String, Object>) snapshot.getValue();
                    latch.countDown();
                }
                
                @Override
                public void onCancelled(DatabaseError error) {
                    latch.countDown();
                }
            });
            
            latch.await(10, TimeUnit.SECONDS);
            return result[0];
            
        } catch (Exception e) {
            throw new RemoteException("Error getting user", e);
        }
    }
    
    @Override
    public boolean updateNotificationPreferences(String userId, Map<String, Boolean> preferences) throws RemoteException {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            boolean[] success = new boolean[1];
            
            database.child("users").child(userId).child("notificationPreferences")
                .setValue(preferences, (error, ref) -> {
                    success[0] = (error == null);
                    latch.countDown();
                });
            
            latch.await(10, TimeUnit.SECONDS);
            return success[0];
            
        } catch (Exception e) {
            throw new RemoteException("Error updating notification preferences", e);
        }
    }
    
    // ========== Reports & Analytics ==========
    
    @Override
    public Map<String, Object> getDailyReport(String warehouseId, String date) throws RemoteException {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Map<String, Object>[] result = new Map[1];
            
            database.child("reports").child(date).child(warehouseId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        result[0] = (Map<String, Object>) snapshot.getValue();
                        latch.countDown();
                    }
                    
                    @Override
                    public void onCancelled(DatabaseError error) {
                        latch.countDown();
                    }
                });
            
            latch.await(10, TimeUnit.SECONDS);
            return result[0];
            
        } catch (Exception e) {
            throw new RemoteException("Error getting daily report", e);
        }
    }
    
    @Override
    public Map<String, Object> getAnalytics(String warehouseId, String startDate, String endDate) throws RemoteException {
        try {
            // Implementation for date range analytics
            Map<String, Object> analytics = new HashMap<>();
            // Add your analytics logic here
            return analytics;
            
        } catch (Exception e) {
            throw new RemoteException("Error getting analytics", e);
        }
    }
    
    // ========== System Operations ==========
    
    @Override
    public Map<String, Object> getSystemHealth() throws RemoteException {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("warehouses", warehouseCache.size());
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }
    
    @Override
    public Map<String, Object> getSystemStats() throws RemoteException {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalWarehouses", warehouseCache.size());
        stats.put("timestamp", System.currentTimeMillis());
        return stats;
    }
    
    @Override
    public long ping() throws RemoteException {
        return System.currentTimeMillis();
    }
}