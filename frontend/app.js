// Firebase Configuration
const firebaseConfig = {
    apiKey: "AIzaSyAvplb95kOR8EWo1poaYxbAzuYGq5GHsXc",
    authDomain: "warehouses-f4498.firebaseapp.com",
    databaseURL: "https://warehouses-f4498-default-rtdb.europe-west1.firebasedatabase.app",
    projectId: "warehouses-f4498",
    storageBucket: "warehouses-f4498.firebasestorage.app",
    messagingSenderId: "907298755172",
    appId: "1:907298755172:web:041e95ade42decce434571",
    measurementId: "G-MEVFHP8LYT"
};

// Initialize Firebase
firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();
const database = firebase.database();

let currentUser = null;
let currentUserData = null;
let warehousesData = {};

// Chart instances
let temperatureChart = null;
let humidityChart = null;
let statusChart = null;

// Role definitions
const ROLES = {
    ADMIN: 'admin',
    OPERATOR: 'operator',
    VIEWER: 'viewer'
};

// Permission checks
function hasPermission(permission) {
    if (!currentUserData || !currentUserData.role) return false;
    
    const role = currentUserData.role;
    
    switch (permission) {
        case 'add_warehouse':
            return role === ROLES.ADMIN || role === ROLES.OPERATOR;
        case 'edit_warehouse':
            return role === ROLES.ADMIN || role === ROLES.OPERATOR;
        case 'delete_warehouse':
            return role === ROLES.ADMIN;
        case 'manage_users':
            return role === ROLES.ADMIN;
        case 'view_reports':
            return true; // All roles can view
        case 'acknowledge_alerts':
            return role === ROLES.ADMIN || role === ROLES.OPERATOR;
        default:
            return false;
    }
}

// Auth State Observer
auth.onAuthStateChanged(user => {
    if (user) {
        currentUser = user;
        loadUserRole().then(() => {
            showDashboard();
            loadUserData();
            startRealtimeMonitoring();
            updateUIBasedOnRole();
        });
    } else {
        currentUser = null;
        currentUserData = null;
        showLogin();
    }
});

// Load user role from database
async function loadUserRole() {
    try {
        const snapshot = await database.ref('users/' + currentUser.uid).once('value');
        currentUserData = snapshot.val();
        
        if (!currentUserData) {
            // Create default viewer role for new users
            currentUserData = {
                email: currentUser.email,
                name: currentUser.displayName || currentUser.email.split('@')[0],
                role: ROLES.VIEWER,
                createdAt: Date.now(),
                lastLogin: Date.now(),
                isActive: true
            };
            await database.ref('users/' + currentUser.uid).set(currentUserData);
        } else {
            // Update last login
            await database.ref('users/' + currentUser.uid + '/lastLogin').set(Date.now());
        }
        
        console.log('User role loaded:', currentUserData.role);
    } catch (error) {
        console.error('Error loading user role:', error);
        currentUserData = { role: ROLES.VIEWER }; // Default to viewer on error
    }
}

// Update UI elements based on user role
function updateUIBasedOnRole() {
    const addWarehouseBtn = document.querySelector('[onclick="showAddWarehouseModal()"]');
    const userRoleBadge = document.getElementById('userRoleBadge');
    const manageUsersBtn = document.getElementById('manageUsersBtn');
    
    // Show role badge with better styling
    if (userRoleBadge && currentUserData) {
        const roleClass = `role-${currentUserData.role}`;
        userRoleBadge.innerHTML = `<span class="user-role-display ${roleClass}">${currentUserData.role}</span>`;
    }
    
    // Show/hide add warehouse button
    if (addWarehouseBtn) {
        addWarehouseBtn.style.display = hasPermission('add_warehouse') ? 'block' : 'none';
    }
    
    // Show/hide manage users button
    if (manageUsersBtn) {
        manageUsersBtn.style.display = hasPermission('manage_users') ? 'inline-block' : 'none';
    }
}

// Switch between login and signup tabs
function switchTab(tab) {
    const loginForm = document.getElementById('loginForm');
    const signupForm = document.getElementById('signupForm');
    const tabs = document.querySelectorAll('.auth-tab');
    
    tabs.forEach(t => t.classList.remove('active'));
    
    if (tab === 'login') {
        loginForm.style.display = 'flex';
        signupForm.style.display = 'none';
        tabs[0].classList.add('active');
    } else {
        loginForm.style.display = 'none';
        signupForm.style.display = 'flex';
        tabs[1].classList.add('active');
    }
}

// Login Form Handler
document.getElementById('loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('loginEmail').value;
    const password = document.getElementById('loginPassword').value;
    const errorDiv = document.getElementById('loginError');
    
    try {
        await auth.signInWithEmailAndPassword(email, password);
    } catch (error) {
        errorDiv.textContent = error.message;
        errorDiv.classList.add('show');
    }
});

// Signup Form Handler
document.getElementById('signupForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('signupName').value;
    const email = document.getElementById('signupEmail').value;
    const role = document.getElementById('signupRole').value;
    const password = document.getElementById('signupPassword').value;
    const confirmPassword = document.getElementById('signupConfirmPassword').value;
    const errorDiv = document.getElementById('signupError');
    
    if (password !== confirmPassword) {
        errorDiv.textContent = 'Passwords do not match';
        errorDiv.classList.add('show');
        return;
    }
    
    if (!role) {
        errorDiv.textContent = 'Please select a role';
        errorDiv.classList.add('show');
        return;
    }
    
    try {
        const userCredential = await auth.createUserWithEmailAndPassword(email, password);
        await database.ref('users/' + userCredential.user.uid).set({
            name: name,
            email: email,
            role: role, // User-selected role during signup
            createdAt: Date.now(),
            lastLogin: Date.now(),
            isActive: true
        });
    } catch (error) {
        errorDiv.textContent = error.message;
        errorDiv.classList.add('show');
    }
});

// Logout
function logout() {
    auth.signOut();
}

// Show/Hide Screens
function showLogin() {
    document.getElementById('loginScreen').classList.add('active');
    document.getElementById('dashboardScreen').classList.remove('active');
}

function showDashboard() {
    document.getElementById('loginScreen').classList.remove('active');
    document.getElementById('dashboardScreen').classList.add('active');
}

// Load User Data
async function loadUserData() {
    const snapshot = await database.ref('users/' + currentUser.uid).once('value');
    const userData = snapshot.val();
    document.getElementById('userName').textContent = userData ? userData.name : currentUser.email;
}

// Show User Management Modal (Admin only)
function showUserManagementModal() {
    if (!hasPermission('manage_users')) {
        alert('You do not have permission to manage users.');
        return;
    }
    document.getElementById('userManagementModal').classList.add('active');
    loadAllUsers();
}

function closeUserManagementModal() {
    document.getElementById('userManagementModal').classList.remove('active');
}

// Load all users for management
async function loadAllUsers() {
    const snapshot = await database.ref('users').once('value');
    const users = snapshot.val() || {};
    
    const usersList = document.getElementById('usersList');
    usersList.innerHTML = Object.entries(users).map(([uid, user]) => `
        <div class="user-item" style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background: #f8fafc; border-radius: 8px; margin-bottom: 12px;">
            <div>
                <div style="font-weight: 600; color: #1e293b;">${user.name}</div>
                <div style="font-size: 14px; color: #64748b;">${user.email}</div>
                <div style="font-size: 12px; color: #94a3b8; margin-top: 4px;">
                    Last login: ${formatTime(user.lastLogin)}
                </div>
            </div>
            <div style="display: flex; align-items: center; gap: 12px;">
                <select onchange="changeUserRole('${uid}', this.value)" style="padding: 8px 12px; border: 2px solid #e2e8f0; border-radius: 6px; font-size: 14px;">
                    <option value="admin" ${user.role === 'admin' ? 'selected' : ''}>Admin</option>
                    <option value="operator" ${user.role === 'operator' ? 'selected' : ''}>Operator</option>
                    <option value="viewer" ${user.role === 'viewer' ? 'selected' : ''}>Viewer</option>
                </select>
                <button onclick="toggleUserStatus('${uid}', ${!user.isActive})" class="btn-secondary" style="padding: 8px 16px;">
                    ${user.isActive ? 'Deactivate' : 'Activate'}
                </button>
            </div>
        </div>
    `).join('');
}

// Change user role
async function changeUserRole(uid, newRole) {
    if (!hasPermission('manage_users')) {
        alert('You do not have permission to change user roles.');
        return;
    }
    
    if (uid === currentUser.uid) {
        alert('You cannot change your own role.');
        return;
    }
    
    try {
        await database.ref('users/' + uid + '/role').set(newRole);
        alert('User role updated successfully!');
    } catch (error) {
        alert('Error updating user role: ' + error.message);
    }
}

// Toggle user active status
async function toggleUserStatus(uid, isActive) {
    if (!hasPermission('manage_users')) {
        alert('You do not have permission to change user status.');
        return;
    }
    
    if (uid === currentUser.uid) {
        alert('You cannot deactivate your own account.');
        return;
    }
    
    try {
        await database.ref('users/' + uid + '/isActive').set(isActive);
        alert(`User ${isActive ? 'activated' : 'deactivated'} successfully!`);
        loadAllUsers();
    } catch (error) {
        alert('Error updating user status: ' + error.message);
    }
}

// Add Warehouse Modal
function showAddWarehouseModal() {
    if (!hasPermission('add_warehouse')) {
        alert('You do not have permission to add warehouses.');
        return;
    }
    document.getElementById('addWarehouseModal').classList.add('active');
}

function closeAddWarehouseModal() {
    document.getElementById('addWarehouseModal').classList.remove('active');
    document.getElementById('addWarehouseForm').reset();
}

// Add Warehouse Form Handler
document.getElementById('addWarehouseForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    if (!hasPermission('add_warehouse')) {
        alert('You do not have permission to add warehouses.');
        return;
    }
    
    const warehouseData = {
        name: document.getElementById('warehouseName').value,
        location: document.getElementById('warehouseLocation').value,
        type: document.getElementById('warehouseType').value,
        minTemp: parseFloat(document.getElementById('minTemp').value),
        maxTemp: parseFloat(document.getElementById('maxTemp').value),
        minHumidity: parseFloat(document.getElementById('minHumidity').value),
        maxHumidity: parseFloat(document.getElementById('maxHumidity').value),
        createdAt: Date.now(),
        userId: currentUser.uid,
        createdBy: currentUserData.name,
        status: 'normal'
    };
    
    const initialReading = {
        temperature: (warehouseData.minTemp + warehouseData.maxTemp) / 2,
        humidity: (warehouseData.minHumidity + warehouseData.maxHumidity) / 2,
        timestamp: Date.now()
    };
    
    try {
        const warehouseRef = database.ref('warehouses').push();
        await warehouseRef.set(warehouseData);
        await database.ref('sensorData/' + warehouseRef.key).push(initialReading);
        
        closeAddWarehouseModal();
    } catch (error) {
        alert('Error adding warehouse: ' + error.message);
    }
});

// Start Real-time Monitoring
function startRealtimeMonitoring() {
    database.ref('warehouses').on('value', (snapshot) => {
        warehousesData = snapshot.val() || {};
        loadSensorData();
        updateWarehouseSelectors();
    });
}

// Update warehouse selectors in charts
function updateWarehouseSelectors() {
    const tempSelect = document.getElementById('tempChartWarehouse');
    const humiditySelect = document.getElementById('humidityChartWarehouse');
    
    if (!tempSelect || !humiditySelect) return;
    
    const warehouseIds = Object.keys(warehousesData);
    const options = '<option value="">Select warehouse...</option>' + 
        warehouseIds.map(id => {
            const warehouse = warehousesData[id];
            return `<option value="${id}">${warehouse.name}</option>`;
        }).join('');
    
    tempSelect.innerHTML = options;
    humiditySelect.innerHTML = options;
    
    // Auto-select first warehouse if available
    if (warehouseIds.length > 0) {
        tempSelect.value = warehouseIds[0];
        humiditySelect.value = warehouseIds[0];
        updateTemperatureChart();
        updateHumidityChart();
    }
}

// Load Sensor Data for all warehouses
async function loadSensorData() {
    const warehouseIds = Object.keys(warehousesData);
    
    for (const id of warehouseIds) {
        database.ref('sensorData/' + id).limitToLast(1).on('value', (snapshot) => {
            const sensorData = snapshot.val();
            if (sensorData) {
                const latestReading = Object.values(sensorData)[0];
                warehousesData[id].latestReading = latestReading;
                checkAlerts(id, latestReading);
            }
            renderDashboard();
        });
    }
    
    renderDashboard();
}

// Check for alerts
function checkAlerts(warehouseId, reading) {
    const warehouse = warehousesData[warehouseId];
    let alertType = null;
    
    if (reading.temperature < warehouse.minTemp || reading.temperature > warehouse.maxTemp) {
        alertType = 'temperature';
    } else if (reading.humidity < warehouse.minHumidity || reading.humidity > warehouse.maxHumidity) {
        alertType = 'humidity';
    }
    
    if (alertType) {
        warehouse.status = 'alert';
        database.ref('warehouses/' + warehouseId + '/status').set('alert');
    } else {
        warehouse.status = 'normal';
        database.ref('warehouses/' + warehouseId + '/status').set('normal');
    }
}

// Render Dashboard
function renderDashboard() {
    const warehouseIds = Object.keys(warehousesData);
    const totalWarehouses = warehouseIds.length;
    const normalCount = warehouseIds.filter(id => warehousesData[id].status === 'normal').length;
    const alertCount = totalWarehouses - normalCount;
    
    document.getElementById('totalWarehouses').textContent = totalWarehouses;
    document.getElementById('normalCount').textContent = normalCount;
    document.getElementById('alertCount').textContent = alertCount;
    
    renderAlerts();
    renderWarehouses();
    updateStatusChart();
}

// Render Alerts
function renderAlerts() {
    const alertsContainer = document.getElementById('alertsContainer');
    const alertWarehouses = Object.entries(warehousesData).filter(([id, w]) => w.status === 'alert');
    
    if (alertWarehouses.length === 0) {
        alertsContainer.innerHTML = '';
        return;
    }
    
    alertsContainer.innerHTML = alertWarehouses.map(([id, warehouse]) => {
        const reading = warehouse.latestReading || {};
        const tempAlert = reading.temperature < warehouse.minTemp || reading.temperature > warehouse.maxTemp;
        const humidityAlert = reading.humidity < warehouse.minHumidity || reading.humidity > warehouse.maxHumidity;
        
        let alertMessage = '';
        if (tempAlert) {
            alertMessage = `Temperature is ${reading.temperature.toFixed(1)}°C (Range: ${warehouse.minTemp}°C - ${warehouse.maxTemp}°C)`;
        } else if (humidityAlert) {
            alertMessage = `Humidity is ${reading.humidity.toFixed(1)}% (Range: ${warehouse.minHumidity}% - ${warehouse.maxHumidity}%)`;
        }
        
        return `
            <div class="alert-banner">
                <div class="alert-content">
                    <div class="alert-icon">⚠️</div>
                    <div class="alert-text">
                        <h4>${warehouse.name}</h4>
                        <p>${alertMessage}</p>
                    </div>
                </div>
                <div class="alert-time">${formatTime(reading.timestamp)}</div>
            </div>
        `;
    }).join('');
}

// Render Warehouses Grid
function renderWarehouses() {
    const warehousesGrid = document.getElementById('warehousesGrid');
    
    if (Object.keys(warehousesData).length === 0) {
        warehousesGrid.innerHTML = '<p style="text-align: center; color: #64748b; grid-column: 1/-1;">No warehouses added yet. Click "Add Warehouse" to get started.</p>';
        return;
    }
    
    warehousesGrid.innerHTML = Object.entries(warehousesData).map(([id, warehouse]) => {
        const reading = warehouse.latestReading || { temperature: 0, humidity: 0, timestamp: Date.now() };
        const statusClass = warehouse.status || 'normal';
        const statusText = statusClass === 'alert' ? 'Alert' : 'Normal';
        
        const canEdit = hasPermission('edit_warehouse');
        const canDelete = hasPermission('delete_warehouse');
        
        return `
            <div class="warehouse-card ${statusClass}" onclick="showWarehouseDetail('${id}')">
                <div class="warehouse-header">
                    <div class="warehouse-info">
                        <h3>${warehouse.name}</h3>
                        <div class="warehouse-location">📍 ${warehouse.location}</div>
                    </div>
                    <span class="status-badge ${statusClass}">${statusText}</span>
                </div>
                <div class="warehouse-type">${warehouse.type}</div>
                <div class="sensor-readings">
                    <div class="reading">
                        <div class="reading-label">Temperature</div>
                        <div class="reading-value">
                            ${reading.temperature.toFixed(1)}<span class="reading-unit">°C</span>
                        </div>
                        <div class="reading-range">Range: ${warehouse.minTemp}°C - ${warehouse.maxTemp}°C</div>
                    </div>
                    <div class="reading">
                        <div class="reading-label">Humidity</div>
                        <div class="reading-value">
                            ${reading.humidity.toFixed(1)}<span class="reading-unit">%</span>
                        </div>
                        <div class="reading-range">Range: ${warehouse.minHumidity}% - ${warehouse.maxHumidity}%</div>
                    </div>
                </div>
                <div class="last-update">Last updated: ${formatTime(reading.timestamp)}</div>
                ${canDelete ? `
                    <div style="margin-top: 12px; padding-top: 12px; border-top: 1px solid #e2e8f0; text-align: center;">
                        <button onclick="event.stopPropagation(); deleteWarehouse('${id}')" class="btn-secondary" style="padding: 6px 12px; font-size: 12px;">Delete</button>
                    </div>
                ` : ''}
            </div>
        `;
    }).join('');
}

// Delete Warehouse (Admin only)
async function deleteWarehouse(warehouseId) {
    if (!hasPermission('delete_warehouse')) {
        alert('You do not have permission to delete warehouses.');
        return;
    }
    
    if (confirm('Are you sure you want to delete this warehouse? This action cannot be undone.')) {
        try {
            await database.ref('warehouses/' + warehouseId).remove();
            await database.ref('sensorData/' + warehouseId).remove();
            await database.ref('alerts/' + warehouseId).remove();
            alert('Warehouse deleted successfully!');
        } catch (error) {
            alert('Error deleting warehouse: ' + error.message);
        }
    }
}

// Show Warehouse Detail
async function showWarehouseDetail(warehouseId) {
    const warehouse = warehousesData[warehouseId];
    const modal = document.getElementById('warehouseDetailModal');
    
    document.getElementById('detailWarehouseName').textContent = warehouse.name;
    
    const historySnapshot = await database.ref('sensorData/' + warehouseId).limitToLast(10).once('value');
    const history = [];
    historySnapshot.forEach(child => {
        history.unshift({ id: child.key, ...child.val() });
    });
    
    const alertsSnapshot = await database.ref('alerts/' + warehouseId).limitToLast(5).once('value');
    const alerts = [];
    alertsSnapshot.forEach(child => {
        alerts.unshift({ id: child.key, ...child.val() });
    });
    
    const latestReading = warehouse.latestReading || {};
    
    document.getElementById('warehouseDetailContent').innerHTML = `
        <div class="detail-section">
            <h4>Warehouse Information</h4>
            <div class="detail-grid">
                <div class="detail-item">
                    <label>Location</label>
                    <div class="value">${warehouse.location}</div>
                </div>
                <div class="detail-item">
                    <label>Storage Type</label>
                    <div class="value">${warehouse.type}</div>
                </div>
                <div class="detail-item">
                    <label>Status</label>
                    <div class="value">
                        <span class="status-badge ${warehouse.status}">${warehouse.status === 'alert' ? 'Alert' : 'Normal'}</span>
                    </div>
                </div>
                <div class="detail-item">
                    <label>Created</label>
                    <div class="value">${new Date(warehouse.createdAt).toLocaleDateString()}</div>
                </div>
                ${warehouse.createdBy ? `
                <div class="detail-item">
                    <label>Created By</label>
                    <div class="value">${warehouse.createdBy}</div>
                </div>
                ` : ''}
            </div>
        </div>
        
        <div class="detail-section">
            <h4>Current Readings</h4>
            <div class="detail-grid">
                <div class="detail-item">
                    <label>Temperature</label>
                    <div class="value">${latestReading.temperature ? latestReading.temperature.toFixed(1) : 'N/A'}°C</div>
                </div>
                <div class="detail-item">
                    <label>Humidity</label>
                    <div class="value">${latestReading.humidity ? latestReading.humidity.toFixed(1) : 'N/A'}%</div>
                </div>
                <div class="detail-item">
                    <label>Temp Range</label>
                    <div class="value">${warehouse.minTemp}°C - ${warehouse.maxTemp}°C</div>
                </div>
                <div class="detail-item">
                    <label>Humidity Range</label>
                    <div class="value">${warehouse.minHumidity}% - ${warehouse.maxHumidity}%</div>
                </div>
            </div>
        </div>
        
        <div class="detail-section">
            <h4>Recent Alerts</h4>
            <div class="history-list">
                ${alerts.length > 0 ? alerts.map(alert => `
                    <div class="history-item alert">
                        <div class="history-time">${formatTime(alert.timestamp)}</div>
                        <div class="history-message">
                            ${alert.type === 'temperature' ? 'Temperature' : 'Humidity'} alert: ${alert.value ? alert.value.toFixed(1) : 'N/A'}${alert.type === 'temperature' ? '°C' : '%'}
                        </div>
                    </div>
                `).join('') : '<p style="color: #64748b; text-align: center;">No alerts recorded</p>'}
            </div>
        </div>
        
        <div class="detail-section">
            <h4>Sensor Reading History</h4>
            <div class="history-list">
                ${history.map(record => `
                    <div class="history-item">
                        <div class="history-time">${formatTime(record.timestamp)}</div>
                        <div class="history-message">
                            Temperature: ${record.temperature.toFixed(1)}°C | Humidity: ${record.humidity.toFixed(1)}%
                        </div>
                    </div>
                `).join('')}
            </div>
        </div>
    `;
    
    modal.classList.add('active');
}

function closeDetailModal() {
    document.getElementById('warehouseDetailModal').classList.remove('active');
}

// Format timestamp
function formatTime(timestamp) {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now - date;
    
    if (diff < 60000) return 'Just now';
    if (diff < 3600000) return Math.floor(diff / 60000) + ' minutes ago';
    if (diff < 86400000) return Math.floor(diff / 3600000) + ' hours ago';
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
}

// Simulate sensor data updates (for testing)
function simulateSensorData() {
    const warehouseIds = Object.keys(warehousesData);
    
    warehouseIds.forEach(id => {
        const warehouse = warehousesData[id];
        const tempRange = warehouse.maxTemp - warehouse.minTemp;
        const humidityRange = warehouse.maxHumidity - warehouse.minHumidity;
        
        const isNormal = Math.random() > 0.2;
        
        let temperature, humidity;
        
        if (isNormal) {
            temperature = warehouse.minTemp + Math.random() * tempRange;
            humidity = warehouse.minHumidity + Math.random() * humidityRange;
        } else {
            if (Math.random() > 0.5) {
                temperature = warehouse.maxTemp + Math.random() * 5;
                humidity = warehouse.minHumidity + Math.random() * humidityRange;
            } else {
                temperature = warehouse.minTemp + Math.random() * tempRange;
                humidity = warehouse.maxHumidity + Math.random() * 20;
            }
        }
        
        database.ref('sensorData/' + id).push({
            temperature: parseFloat(temperature.toFixed(2)),
            humidity: parseFloat(humidity.toFixed(2)),
            timestamp: Date.now()
        });
    });
}

// Start simulation every 30 seconds (for testing purposes)
setInterval(() => {
    if (currentUser && Object.keys(warehousesData).length > 0) {
        simulateSensorData();
    }
}, 30000);

// ========== CHART.JS FUNCTIONS ==========

// Initialize Temperature Chart
async function updateTemperatureChart() {
    const warehouseId = document.getElementById('tempChartWarehouse').value;
    if (!warehouseId) {
        if (temperatureChart) temperatureChart.destroy();
        return;
    }
    
    const warehouse = warehousesData[warehouseId];
    const snapshot = await database.ref('sensorData/' + warehouseId).limitToLast(20).once('value');
    
    const labels = [];
    const data = [];
    const minLine = [];
    const maxLine = [];
    
    snapshot.forEach(child => {
        const reading = child.val();
        labels.push(new Date(reading.timestamp).toLocaleTimeString());
        data.push(reading.temperature);
        minLine.push(warehouse.minTemp);
        maxLine.push(warehouse.maxTemp);
    });
    
    const ctx = document.getElementById('temperatureChart').getContext('2d');
    
    if (temperatureChart) {
        temperatureChart.destroy();
    }
    
    temperatureChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                {
                    label: 'Temperature (°C)',
                    data: data,
                    borderColor: '#2563eb',
                    backgroundColor: 'rgba(37, 99, 235, 0.1)',
                    borderWidth: 3,
                    fill: true,
                    tension: 0.4,
                    pointRadius: 4,
                    pointHoverRadius: 6
                },
                {
                    label: 'Min Threshold',
                    data: minLine,
                    borderColor: '#059669',
                    borderWidth: 2,
                    borderDash: [5, 5],
                    fill: false,
                    pointRadius: 0
                },
                {
                    label: 'Max Threshold',
                    data: maxLine,
                    borderColor: '#dc2626',
                    borderWidth: 2,
                    borderDash: [5, 5],
                    fill: false,
                    pointRadius: 0
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    display: true,
                    position: 'top'
                },
                title: {
                    display: true,
                    text: warehouse.name + ' - Temperature Monitoring'
                }
            },
            scales: {
                y: {
                    beginAtZero: false,
                    title: {
                        display: true,
                        text: 'Temperature (°C)'
                    }
                },
                x: {
                    title: {
                        display: true,
                        text: 'Time'
                    }
                }
            }
        }
    });
}

// Initialize Humidity Chart
async function updateHumidityChart() {
    const warehouseId = document.getElementById('humidityChartWarehouse').value;
    if (!warehouseId) {
        if (humidityChart) humidityChart.destroy();
        return;
    }
    
    const warehouse = warehousesData[warehouseId];
    const snapshot = await database.ref('sensorData/' + warehouseId).limitToLast(20).once('value');
    
    const labels = [];
    const data = [];
    const minLine = [];
    const maxLine = [];
    
    snapshot.forEach(child => {
        const reading = child.val();
        labels.push(new Date(reading.timestamp).toLocaleTimeString());
        data.push(reading.humidity);
        minLine.push(warehouse.minHumidity);
        maxLine.push(warehouse.maxHumidity);
    });
    
    const ctx = document.getElementById('humidityChart').getContext('2d');
    
    if (humidityChart) {
        humidityChart.destroy();
    }
    
    humidityChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                {
                    label: 'Humidity (%)',
                    data: data,
                    borderColor: '#7c3aed',
                    backgroundColor: 'rgba(124, 58, 237, 0.1)',
                    borderWidth: 3,
                    fill: true,
                    tension: 0.4,
                    pointRadius: 4,
                    pointHoverRadius: 6
                },
                {
                    label: 'Min Threshold',
                    data: minLine,
                    borderColor: '#059669',
                    borderWidth: 2,
                    borderDash: [5, 5],
                    fill: false,
                    pointRadius: 0
                },
                {
                    label: 'Max Threshold',
                    data: maxLine,
                    borderColor: '#dc2626',
                    borderWidth: 2,
                    borderDash: [5, 5],
                    fill: false,
                    pointRadius: 0
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    display: true,
                    position: 'top'
                },
                title: {
                    display: true,
                    text: warehouse.name + ' - Humidity Monitoring'
                }
            },
            scales: {
                y: {
                    beginAtZero: false,
                    title: {
                        display: true,
                        text: 'Humidity (%)'
                    }
                },
                x: {
                    title: {
                        display: true,
                        text: 'Time'
                    }
                }
            }
        }
    });
}

// Update Status Chart (Pie/Doughnut)
function updateStatusChart() {
    const warehouseIds = Object.keys(warehousesData);
    const normalCount = warehouseIds.filter(id => warehousesData[id].status === 'normal').length;
    const alertCount = warehouseIds.length - normalCount;
    
    const ctx = document.getElementById('statusChart').getContext('2d');
    
    if (statusChart) {
        statusChart.data.datasets[0].data = [normalCount, alertCount];
        statusChart.update();
        return;
    }
    
    statusChart = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: ['Normal Status', 'Alert Status'],
            datasets: [{
                data: [normalCount, alertCount],
                backgroundColor: [
                    'rgba(5, 150, 105, 0.8)',
                    'rgba(220, 38, 38, 0.8)'
                ],
                borderColor: [
                    '#059669',
                    '#dc2626'
                ],
                borderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    display: true,
                    position: 'bottom'
                },
                title: {
                    display: false
                }
            }
        }
    });
}

// Auto-update charts every 30 seconds
setInterval(() => {
    if (currentUser && Object.keys(warehousesData).length > 0) {
        const tempWarehouse = document.getElementById('tempChartWarehouse')?.value;
        const humidityWarehouse = document.getElementById('humidityChartWarehouse')?.value;
        
        if (tempWarehouse) updateTemperatureChart();
        if (humidityWarehouse) updateHumidityChart();
    }
}, 30000);