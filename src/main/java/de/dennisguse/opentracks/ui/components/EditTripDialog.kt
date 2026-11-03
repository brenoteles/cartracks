package de.dennisguse.opentracks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.theme.*

@Composable
fun EditTripDialog(
    trip: TripItem,
    onConfirm: (newName: String, newDescription: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    // Attempt to extract origin and destination if the name has an arrow
    val parts = remember(trip.name) {
        if (trip.name.contains("➔")) {
            trip.name.split("➔").map { it.trim() }
        } else if (trip.name.contains("->")) {
            trip.name.split("->").map { it.trim() }
        } else {
            listOf(trip.name, "")
        }
    }

    var origin by remember { mutableStateOf(if (parts.isNotEmpty()) parts[0] else "") }
    var destination by remember { mutableStateOf(if (parts.size > 1) parts[1] else "") }
    var title by remember { mutableStateOf(trip.name) }
    var description by remember { mutableStateOf(trip.description) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text("Excluir Viagem", fontWeight = FontWeight.Bold, color = WandaRed10)
            },
            text = {
                Text(
                    "Tem certeza que deseja apagar o registro desta viagem? Esta ação não pode ser desfeita.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WandaRed10)
                ) {
                    Text("Excluir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = WandaCardElevated
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(WandaInteractive.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = WandaInteractive,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Editar Viagem",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Origem (Início)
                OutlinedTextField(
                    value = origin,
                    onValueChange = {
                        origin = it
                        if (destination.isNotBlank()) {
                            title = "$it ➔ $destination"
                        } else if (it.isNotBlank()) {
                            title = it
                        }
                    },
                    label = { Text("Ponto de Início (Origem)") },
                    placeholder = { Text("Ex: Minha Casa, Av. Paulista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Destino
                OutlinedTextField(
                    value = destination,
                    onValueChange = {
                        destination = it
                        if (origin.isNotBlank() && it.isNotBlank()) {
                            title = "$origin ➔ $it"
                        } else if (it.isNotBlank()) {
                            title = it
                        }
                    },
                    label = { Text("Ponto de Destino") },
                    placeholder = { Text("Ex: Escritório, Shopping") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Título Completo
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome / Título da Viagem") },
                    placeholder = { Text("Ex: Casa ➔ Trabalho") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Descrição / Observações
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição e Notas") },
                    placeholder = { Text("Ex: Trânsito moderado, tempo bom") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                // Botão de excluir
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WandaRed10),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apagar esta Viagem", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.ifBlank {
                        if (origin.isNotBlank() && destination.isNotBlank()) "$origin ➔ $destination"
                        else if (origin.isNotBlank()) origin
                        else trip.name
                    }
                    onConfirm(finalTitle.trim(), description.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = WandaInteractive)
            ) {
                Text("Salvar Alterações", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        },
        containerColor = WandaCardElevated
    )
}
