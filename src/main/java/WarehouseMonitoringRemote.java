import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * Remote interface for Warehouse Monitoring System
 * Allows remote clients to interact with the monitoring system
 */
public interface WarehouseMonitoringRemote extends Remote {
    
    // ========== Warehouse Operations ==========
    
    /**
     * Get all warehouses for a user
     * @param userId User ID
     * @return Map of warehouse IDs to warehouse data
     */
    Map<String, Map<String, Object>> getWarehouses(String userId) throws RemoteException;
    
    /**
     * Get specific warehouse details
     * @param warehouseId Warehouse ID
     * @return Warehouse data
     */
    Map<String, Object> getWarehouse(String warehouseId) throws RemoteException;
    
    /**
     * Add new warehouse
     * @param userId Owner user ID
     * @param warehouseData Warehouse configuration
     * @return New warehouse ID
     */
    String addWarehouse(String userId, Map<String, Object> warehouseData) throws RemoteException;
    
    /**
     * Update warehouse configuration
     * @param warehouseId Warehouse ID
     * @param userId User ID (for permission check)
     * @param updates Updated fields
     * @return Success status
     */
    boolean updateWarehouse(String warehouseId, String userId, Map<String, Object> updates) throws RemoteException;
    
    /**
     * Delete warehouse
     * @param warehouseId Warehouse ID
     * @param userId User ID (for permission check)
     * @return Success status
     */
    boolean deleteWarehouse(String warehouseId, String userId) throws RemoteException;
    
    // ========== Sensor Data Operations ==========
    
    /**
     * Get latest sensor reading for a warehouse
     * @param warehouseId Warehouse ID
     * @return Latest sensor reading
     */
    Map<String, Object> getLatestReading(String warehouseId) throws RemoteException;
    
    /**
     * Get sensor reading history
     * @param warehouseId Warehouse ID
     * @param limit Number of readings to retrieve
     * @return List of sensor readings
     */
    List<Map<String, Object>> getSensorHistory(String warehouseId, int limit) throws RemoteException;
    
    /**
     * Add sensor reading manually
     * @param warehouseId Warehouse ID
     * @param temperature Temperature value
     * @param humidity Humidity value
     * @return Success status
     */
    boolean addSensorReading(String warehouseId, double temperature, double humidity) throws RemoteException;
    
    // ========== Alert Operations ==========
    
    /**
     * Get active alerts for a warehouse
     * @param warehouseId Warehouse ID
     * @return List of active alerts
     */
    List<Map<String, Object>> getAlerts(String warehouseId) throws RemoteException;
    
    /**
     * Get all active alerts across all warehouses
     * @param userId User ID
     * @return List of all alerts
     */
    List<Map<String, Object>> getAllAlerts(String userId) throws RemoteException;
    
    /**
     * Acknowledge an alert
     * @param warehouseId Warehouse ID
     * @param alertId Alert ID
     * @param userId User ID
     * @return Success status
     */
    boolean acknowledgeAlert(String warehouseId, String alertId, String userId) throws RemoteException;
    
    // ========== User Operations ==========
    
    /**
     * Get user information
     * @param userId User ID
     * @return User data
     */
    Map<String, Object> getUser(String userId) throws RemoteException;
    
    /**
     * Update user notification preferences
     * @param userId User ID
     * @param preferences Notification preferences
     * @return Success status
     */
    boolean updateNotificationPreferences(String userId, Map<String, Boolean> preferences) throws RemoteException;
    
    // ========== Reports & Analytics ==========
    
    /**
     * Get daily report for a warehouse
     * @param warehouseId Warehouse ID
     * @param date Date in format yyyy-MM-dd
     * @return Report data
     */
    Map<String, Object> getDailyReport(String warehouseId, String date) throws RemoteException;
    
    /**
     * Get analytics data for a warehouse
     * @param warehouseId Warehouse ID
     * @param startDate Start date (yyyy-MM-dd)
     * @param endDate End date (yyyy-MM-dd)
     * @return Analytics data
     */
    Map<String, Object> getAnalytics(String warehouseId, String startDate, String endDate) throws RemoteException;
    
    // ========== System Operations ==========
    
    /**
     * Get system health status
     * @return System health information
     */
    Map<String, Object> getSystemHealth() throws RemoteException;
    
    /**
     * Get system statistics
     * @return System statistics
     */
    Map<String, Object> getSystemStats() throws RemoteException;
    
    /**
     * Test connection
     * @return Server timestamp
     */
    long ping() throws RemoteException;
}

