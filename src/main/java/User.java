import java.util.*;

public class User {
    private String uid;
    private String email;
    private String name;
    private UserRole role;
    private long createdAt;
    private long lastLogin;
    private boolean isActive;
    
    // Enhanced fields
    private String department;
    private String phoneNumber;
    private Map<String, Boolean> notificationPreferences;
    private List<String> assignedWarehouses; // Specific warehouses this user can access
    private AuditInfo auditInfo;
    
    public enum UserRole {
        ADMIN("admin", 3),
        OPERATOR("operator", 2),
        VIEWER("viewer", 1);
        
        private final String value;
        private final int priority;
        
        UserRole(String value, int priority) {
            this.value = value;
            this.priority = priority;
        }
        
        public String getValue() {
            return value;
        }
        
        public int getPriority() {
            return priority;
        }
        
        public static UserRole fromString(String value) {
            for (UserRole role : UserRole.values()) {
                if (role.value.equalsIgnoreCase(value)) {
                    return role;
                }
            }
            return VIEWER;
        }
        
        // Permission checks with granular control
        public boolean canAddWarehouse() {
            return this == ADMIN || this == OPERATOR;
        }
        
        public boolean canEditWarehouse() {
            return this == ADMIN || this == OPERATOR;
        }
        
        public boolean canDeleteWarehouse() {
            return this == ADMIN;
        }
        
        public boolean canManageUsers() {
            return this == ADMIN;
        }
        
        public boolean canViewReports() {
            return true;
        }
        
        public boolean canAcknowledgeAlerts() {
            return this == ADMIN || this == OPERATOR;
        }
        
        public boolean canExportData() {
            return this == ADMIN;
        }
        
        public boolean canConfigureThresholds() {
            return this == ADMIN;
        }
        
        public boolean canViewAnalytics() {
            return this == ADMIN || this == OPERATOR;
        }
    }
    
    // Audit information class
    public static class AuditInfo {
        private String createdBy;
        private long createdAt;
        private String lastModifiedBy;
        private long lastModifiedAt;
        private int loginCount;
        private String lastIpAddress;
        
        public AuditInfo() {
            this.createdAt = System.currentTimeMillis();
            this.lastModifiedAt = System.currentTimeMillis();
            this.loginCount = 0;
        }
        
        public void recordLogin(String ipAddress) {
            this.loginCount++;
            this.lastIpAddress = ipAddress;
        }
        
        // Getters and setters
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        
        public String getLastModifiedBy() { return lastModifiedBy; }
        public void setLastModifiedBy(String lastModifiedBy) { 
            this.lastModifiedBy = lastModifiedBy;
            this.lastModifiedAt = System.currentTimeMillis();
        }
        
        public long getLastModifiedAt() { return lastModifiedAt; }
        public void setLastModifiedAt(long lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }
        
        public int getLoginCount() { return loginCount; }
        public void setLoginCount(int loginCount) { this.loginCount = loginCount; }
        
        public String getLastIpAddress() { return lastIpAddress; }
        public void setLastIpAddress(String lastIpAddress) { this.lastIpAddress = lastIpAddress; }
    }
    
    public User() {
        this.notificationPreferences = new HashMap<>();
        this.assignedWarehouses = new ArrayList<>();
        this.auditInfo = new AuditInfo();
        initializeDefaultNotifications();
    }
    
    public User(String uid, String email, String name, UserRole role) {
        this();
        this.uid = uid;
        this.email = email;
        this.name = name;
        this.role = role;
        this.createdAt = System.currentTimeMillis();
        this.lastLogin = System.currentTimeMillis();
        this.isActive = true;
    }
    
    private void initializeDefaultNotifications() {
        notificationPreferences.put("email_alerts", true);
        notificationPreferences.put("critical_alerts", true);
        notificationPreferences.put("warning_alerts", true);
        notificationPreferences.put("daily_reports", false);
        notificationPreferences.put("weekly_summary", false);
    }
    
    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    
    public String getRoleString() { 
        return role != null ? role.getValue() : "viewer"; 
    }
    public void setRoleString(String roleStr) { 
        this.role = UserRole.fromString(roleStr); 
    }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getLastLogin() { return lastLogin; }
    public void setLastLogin(long lastLogin) { this.lastLogin = lastLogin; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public Map<String, Boolean> getNotificationPreferences() { return notificationPreferences; }
    public void setNotificationPreferences(Map<String, Boolean> prefs) { 
        this.notificationPreferences = prefs; 
    }
    
    public List<String> getAssignedWarehouses() { return assignedWarehouses; }
    public void setAssignedWarehouses(List<String> warehouses) { 
        this.assignedWarehouses = warehouses; 
    }
    
    public AuditInfo getAuditInfo() { return auditInfo; }
    public void setAuditInfo(AuditInfo auditInfo) { this.auditInfo = auditInfo; }
    
    // Enhanced permission methods
    public boolean hasPermission(String permission) {
        if (!isActive || role == null) return false;
        
        switch (permission) {
            case "add_warehouse": return role.canAddWarehouse();
            case "edit_warehouse": return role.canEditWarehouse();
            case "delete_warehouse": return role.canDeleteWarehouse();
            case "manage_users": return role.canManageUsers();
            case "view_reports": return role.canViewReports();
            case "acknowledge_alerts": return role.canAcknowledgeAlerts();
            case "export_data": return role.canExportData();
            case "configure_thresholds": return role.canConfigureThresholds();
            case "view_analytics": return role.canViewAnalytics();
            default: return false;
        }
    }
    
    // Check if user can access specific warehouse
    public boolean canAccessWarehouse(String warehouseId) {
        if (role == UserRole.ADMIN) return true; // Admins can access all
        if (assignedWarehouses == null || assignedWarehouses.isEmpty()) return true; // No restrictions
        return assignedWarehouses.contains(warehouseId);
    }
    
    // Add warehouse access
    public void assignWarehouse(String warehouseId) {
        if (assignedWarehouses == null) {
            assignedWarehouses = new ArrayList<>();
        }
        if (!assignedWarehouses.contains(warehouseId)) {
            assignedWarehouses.add(warehouseId);
        }
    }
    
    // Remove warehouse access
    public void unassignWarehouse(String warehouseId) {
        if (assignedWarehouses != null) {
            assignedWarehouses.remove(warehouseId);
        }
    }
    
    // Update notification preference
    public void setNotificationPreference(String key, boolean value) {
        if (notificationPreferences == null) {
            notificationPreferences = new HashMap<>();
        }
        notificationPreferences.put(key, value);
    }
    
    // Check notification preference
    public boolean shouldReceiveNotification(String type) {
        if (notificationPreferences == null) return true;
        return notificationPreferences.getOrDefault(type, true);
    }
    
    // Record login
    public void recordLogin(String ipAddress) {
        this.lastLogin = System.currentTimeMillis();
        if (auditInfo != null) {
            auditInfo.recordLogin(ipAddress);
        }
    }
    
    // Validate user
    public boolean isValid() {
        return uid != null && !uid.isEmpty() &&
               email != null && !email.isEmpty() &&
               name != null && !name.isEmpty() &&
               role != null;
    }
    
    // Get display name for UI
    public String getDisplayName() {
        return name + " (" + getRoleString() + ")";
    }
    
    // Check if account is locked (e.g., too many failed logins)
    public boolean isAccountLocked() {
        // Implementation would check for security flags
        return !isActive;
    }
    
    @Override
    public String toString() {
        return String.format("User{uid='%s', name='%s', email='%s', role=%s, active=%s}", 
                           uid, name, email, role, isActive);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(uid, user.uid);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(uid);
    }
}