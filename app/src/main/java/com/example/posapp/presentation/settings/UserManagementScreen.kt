package com.example.posapp.presentation.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.posapp.data.local.entity.UserEntity
import com.example.posapp.data.local.entity.UserRole
import com.example.posapp.presentation.theme.PosBrandedTopBar

/** Layar manajemen kasir/admin & PIN login (fitur multi-user). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    viewModel: UserManagementViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var userPendingDelete by remember { mutableStateOf<UserEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UserManagementEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PosBrandedTopBar(
                title = { Text("Pengguna & Login PIN") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        },
        floatingActionButton = {
            // navigationBarsPadding() mencegah FAB terpotong system nav bar di mode lanskap.
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.navigationBarsPadding()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Pengguna")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Wajibkan Login PIN", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Kasir/admin harus memasukkan PIN setiap kali membuka aplikasi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.pinLoginEnabled,
                        onCheckedChange = { viewModel.setPinLoginEnabled(it) }
                    )
                }
            }

            if (uiState.pinLoginEnabled) {
                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Auto-Lock Idle", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Otomatis logout kalau app tidak disentuh selama durasi ini. " +
                                "Terpisah dari kunci-saat-app-ditutup yang selalu aktif.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Mati", 1 to "1 mnt", 5 to "5 mnt", 15 to "15 mnt", 30 to "30 mnt")
                                .forEach { (minutes, label) ->
                                    FilterChip(
                                        selected = uiState.autoLockMinutes == minutes,
                                        onClick = { viewModel.setAutoLockMinutes(minutes) },
                                        label = { Text(label) }
                                    )
                                }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Daftar Pengguna", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (uiState.users.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada pengguna. Tap + untuk menambahkan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn {
                    items(uiState.users, key = { it.id }) { user ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when (user.role) {
                                        UserRole.ADMIN -> "Admin"
                                        UserRole.MANAGER -> "Manager"
                                        UserRole.KASIR -> "Kasir"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { userPendingDelete = user }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddUserDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, pin, role ->
                viewModel.addUser(name, pin, role)
                showAddDialog = false
            }
        )
    }

    userPendingDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { userPendingDelete = null },
            title = { Text("Hapus pengguna?") },
            text = { Text("\"${user.name}\" tidak akan bisa login lagi.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteUser(user); userPendingDelete = null }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { userPendingDelete = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun AddUserDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, pin: String, role: UserRole) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.KASIR) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("Tambah Pengguna", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nama") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("PIN (4-6 digit)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(selected = role == UserRole.KASIR, onClick = { role = UserRole.KASIR }, label = { Text("Kasir") })
                    FilterChip(selected = role == UserRole.MANAGER, onClick = { role = UserRole.MANAGER }, label = { Text("Manager") })
                    FilterChip(selected = role == UserRole.ADMIN, onClick = { role = UserRole.ADMIN }, label = { Text("Admin") })
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(name, pin, role) }) { Text("Simpan") }
                }
            }
        }
    }
}
