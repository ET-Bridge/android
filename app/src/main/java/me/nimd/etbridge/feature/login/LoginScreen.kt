package me.nimd.etbridge.feature.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.nimd.etbridge.R
import me.nimd.etbridge.core.designsystem.EtMotion
import me.nimd.etbridge.core.model.Institute
import me.nimd.etbridge.ui.keyedUniquely

@Composable
fun LoginScreen(vm: LoginViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    // Gentle entrance: logo floats in, form rises.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, EtMotion.slow()) }
    val glow = rememberInfiniteTransition(label = "glow")
    val glowAlpha by glow.animateFloat(0.25f, 0.55f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "a")

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.35f), MaterialTheme.colorScheme.background),
                        radius = 1400f,
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Image(
                painterResource(R.mipmap.ic_launcher_foreground), null,
                Modifier.size(132.dp).graphicsLayer {
                    alpha = entrance.value
                    scaleX = 0.8f + 0.2f * entrance.value
                    scaleY = 0.8f + 0.2f * entrance.value
                },
            )
            Text("ETBridge", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "A fast, beautiful client for your ETLab portal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = entrance.value
                        translationY = (1f - entrance.value) * 60f
                    }
                    .animateContentSize(EtMotion.smooth()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InstituteField(state.selected, state.institutesLoading) { pickerOpen = true }
                OutlinedTextField(
                    value = state.username,
                    onValueChange = vm::onUsername,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username / admission no.") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = vm::onPassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                if (showPassword) "Hide password" else "Show password",
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); vm.submit() }),
                    shape = MaterialTheme.shapes.medium,
                )

                AnimatedVisibility(
                    visible = state.error != null,
                    enter = expandVertically(EtMotion.smooth()) + fadeIn(),
                    exit = shrinkVertically(EtMotion.smooth()) + fadeOut(),
                ) {
                    Text(
                        state.error.orEmpty(),
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    onClick = { focus.clearFocus(); vm.submit() },
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Sign in", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = vm::demo,
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Rounded.PlayCircle, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Explore the demo")
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.Shield, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Your credentials go straight to ETLab's official API. The session token is encrypted on this device and never sent anywhere else.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (pickerOpen) {
        InstitutePicker(
            institutes = state.institutes,
            onPick = { vm.select(it); pickerOpen = false },
            onCustom = { vm.useCustomCode(it); pickerOpen = false },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun InstituteField(selected: Institute?, loading: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AccountBalance, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text("College", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    selected?.name ?: if (loading) "Loading colleges…" else "Select your college",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
            }
            if (selected != null) {
                Text(
                    selected.id.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstitutePicker(
    institutes: List<Institute>,
    onPick: (Institute) -> Unit,
    onCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, institutes) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) institutes else institutes.filter { i ->
            i.name.lowercase().contains(q) || i.id.lowercase().contains(q) ||
                i.shortName.orEmpty().lowercase().contains(q) || i.keywords.any { it.lowercase().contains(q) }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Choose your college", style = MaterialTheme.typography.titleLarge)
            Text(
                "Your code is the subdomain of your ETLab portal, e.g. gcek for gcek.etlab.app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search name or code") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().height(460.dp)) {
            if (query.isNotBlank() && filtered.none { it.id.equals(query.trim(), true) }) {
                item("custom") {
                    ListItem(
                        headlineContent = { Text("Use code “${query.trim().lowercase()}”") },
                        supportingContent = { Text("If your college isn't listed") },
                        leadingContent = { Icon(Icons.Rounded.AccountBalance, null) },
                        modifier = Modifier.clickable { onCustom(query) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                    HorizontalDivider()
                }
            }
            items(filtered.keyedUniquely { it.id }, key = { it.first }) { (_, inst) ->
                ListItem(
                    headlineContent = { Text(inst.name) },
                    supportingContent = { Text(inst.id.uppercase()) },
                    modifier = Modifier.clickable { onPick(inst) }.animateItem(),
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}
