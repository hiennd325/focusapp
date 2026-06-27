package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.*
import com.example.viewmodel.FocusViewModel
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if launched as a redirection lock from the blocker service
        val isBlockedTrigger = intent.getBooleanExtra("BLOCKED_TRIGGER", false)
        if (isBlockedTrigger) {
            Toast.makeText(this, "⚠️ Steel Focus Lock Mode is active!", Toast.LENGTH_LONG).show()
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainScreenContent(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreenContent(
    modifier: Modifier = Modifier,
    viewModel: FocusViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()
    
    // Check permission stats periodically on screen resume/enter
    var hasUsagePermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }

    LaunchedEffect(currentScreen) {
        hasUsagePermission = viewModel.isUsageAccessGranted(context)
        hasOverlayPermission = viewModel.isOverlayAccessGranted(context)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SteelGrey900)
    ) {
        // High luxury professional background mesh/grid
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridStep = 40.dp.toPx()
            val gridWidth = size.width
            val gridHeight = size.height
            
            // Draw clean vertical gradient background (light palette FDFCFF -> EFF1F7)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(SteelGrey900, SteelGrey800)
                ),
                size = size
            )
            
            var x = 0f
            while (x < gridWidth) {
                drawLine(
                    color = SteelGrey700.copy(alpha = 0.25f),
                    start = Offset(x, 0f),
                    end = Offset(x, gridHeight),
                    strokeWidth = 1f
                )
                x += gridStep
            }
            var y = 0f
            while (y < gridHeight) {
                drawLine(
                    color = SteelGrey700.copy(alpha = 0.25f),
                    start = Offset(0f, y),
                    end = Offset(gridWidth, y),
                    strokeWidth = 1f
                )
                y += gridStep
            }
        }

        // Screen Content Wrapper (offset bottom by bottom nav height)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (currentScreen != "LOCKED") 80.dp else 0.dp)
        ) {
            // Parent routing transition
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) with
                    fadeOut(animationSpec = androidx.compose.animation.core.tween(220))
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    "HOME" -> HomeScreen(viewModel, hasUsagePermission, hasOverlayPermission)
                    "LOCKED" -> LockedScreen(viewModel)
                    "AI_COACH" -> AiCoachScreen(viewModel)
                    "SETTINGS" -> SettingsScreen(viewModel, hasUsagePermission, hasOverlayPermission)
                    else -> HomeScreen(viewModel, hasUsagePermission, hasOverlayPermission)
                }
            }
        }

        // Custom M3 styled Professional Polish Bottom Navigation Bar
        if (currentScreen != "LOCKED") {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color.White)
                    .border(BorderStroke(1.dp, SteelGrey700))
                    .windowInsetsPadding(WindowInsets.navigationBars),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Control (Home) Item
                val isHome = currentScreen == "HOME"
                Column(
                    modifier = Modifier
                        .clickable { viewModel.navigateTo("HOME") }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Control",
                        tint = if (isHome) SteelCrimson else Color(0xFF74777F),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Control",
                        fontSize = 11.sp,
                        fontWeight = if (isHome) FontWeight.Bold else FontWeight.Medium,
                        color = if (isHome) SteelCrimson else Color(0xFF74777F)
                    )
                }

                // AI Coach Item
                val isCoach = currentScreen == "AI_COACH"
                Column(
                    modifier = Modifier
                        .clickable { viewModel.navigateTo("AI_COACH") }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "AI Coach",
                        tint = if (isCoach) SteelCrimson else Color(0xFF74777F),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "AI Coach",
                        fontSize = 11.sp,
                        fontWeight = if (isCoach) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCoach) SteelCrimson else Color(0xFF74777F)
                    )
                }

                // Settings Item
                val isSettings = currentScreen == "SETTINGS"
                Column(
                    modifier = Modifier
                        .clickable { viewModel.navigateTo("SETTINGS") }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = if (isSettings) SteelCrimson else Color(0xFF74777F),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Settings",
                        fontSize = 11.sp,
                        fontWeight = if (isSettings) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSettings) SteelCrimson else Color(0xFF74777F)
                    )
                }
            }
        }
    }
}

// ======================= HOME SCREEN =======================
@Composable
fun HomeScreen(
    viewModel: FocusViewModel,
    hasUsage: Boolean,
    hasOverlay: Boolean
) {
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val streakDays by viewModel.streakDays.collectAsState()
    val checkInStreak by viewModel.checkInStreak.collectAsState()

    var focusMinutesSelected by remember { mutableStateOf(25) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
    ) {
        // App header containing name and state labels
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "FocusLock AI",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif,
                            color = SteelCrimsonLight,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "STEEL DISCIPLINE • ANTI-PROCRASTINATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = SteelGrayText,
                            letterSpacing = 2.sp
                        )
                    )
                }
                
                // Active status indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (hasUsage && hasOverlay) SteelEmerald.copy(alpha = 0.15f) else SteelAmber.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                1.dp,
                                if (hasUsage && hasOverlay) SteelEmerald else SteelAmber,
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (hasUsage && hasOverlay) SteelEmerald else SteelAmber, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasUsage && hasOverlay) "Shield: ON" else "Setup Required",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (hasUsage && hasOverlay) SteelEmerald else SteelAmber
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = { viewModel.navigateTo("SETTINGS") },
                        modifier = Modifier
                            .background(SteelGrey700, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = PremiumWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Daily Hero streak banner
        item {
            FocusStreakCard(streakDays = checkInStreak)
        }

        // Core interactive slider control card for steel session
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = SteelGrey800),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, SteelGrey700),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("focus_trigger_card")
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = SteelCrimsonLight,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Activate Deep Focus Lock",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = PremiumWhite
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Configure your discipline session. Once started, restricted apps will be strictly locked down!",
                        fontSize = 13.sp,
                        color = SteelGrayText,
                        lineHeight = 18.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Large visual timing badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SteelGrey700, RoundedCornerShape(16.dp))
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "$focusMinutesSelected",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = SteelCrimsonLight
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Minutes",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PremiumWhite
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Customized timing ticks select
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 25, 45, 60, 120).forEach { mins ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (focusMinutesSelected == mins) SteelCrimson.copy(alpha = 0.15f) else SteelGrey700,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (focusMinutesSelected == mins) SteelCrimson else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { focusMinutesSelected = mins }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${mins}M",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (focusMinutesSelected == mins) SteelCrimsonLight else PremiumWhite
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.startFocusSession(focusMinutesSelected) },
                        colors = ButtonDefaults.buttonColors(containerColor = SteelCrimson),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("start_focus_btn")
                    ) {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Hard Lock Now",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Targeted Restricted Application list
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Restricted Entertainment Apps",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = PremiumWhite
                )
            )
            Text(
                text = "Attempting to open these will redirect you back to FocusLock",
                fontSize = 11.sp,
                color = SteelGrayText,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val appBlacklist = listOf(
                Pair("TikTok", "com.zhiliaoapp.musically"),
                Pair("Facebook", "com.facebook.katana"),
                Pair("Instagram", "com.instagram.android"),
                Pair("YouTube", "com.android.youtube"),
                Pair("Chrome", "com.android.chrome")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                appBlacklist.forEach { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SteelGrey800),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SteelGrey700),
                        modifier = Modifier
                            .width(130.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .border(BorderStroke(1.dp, SteelGrey700), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val iconColor = when (item.first) {
                                    "Chrome" -> Color(0xFF4285F4)
                                    "YouTube" -> Color(0xFFFF0000)
                                    "TikTok" -> Color(0xFF111111)
                                    "Facebook" -> Color(0xFF1877F2)
                                    "Instagram" -> Color(0xFFE1306C)
                                    else -> SteelCrimson
                                }
                                val iconVector = when (item.first) {
                                    "Chrome" -> Icons.Default.Language
                                    "YouTube" -> Icons.Filled.PlayCircle
                                    "TikTok" -> Icons.Default.VideoLibrary
                                    "Facebook" -> Icons.Default.ChatBubble
                                    "Instagram" -> Icons.Default.PhotoCamera
                                    else -> Icons.Default.Lock
                                }
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.first,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PremiumWhite
                            )
                            Text(
                                text = "Shield Active",
                                fontSize = 10.sp,
                                color = SteelEmerald
                            )
                        }
                    }
                }
            }
        }

        // AI coach banner
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = SteelGrey800),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SteelCrimson.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.navigateTo("AI_COACH") }
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(SteelAmber.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Coach",
                            tint = SteelAmber,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Productivity Habit AI Analysis",
                            color = PremiumWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Analyze distraction logs and receive your optimized discipline timeline schedule.",
                            color = SteelGrayText,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForwardIos,
                        contentDescription = "Go",
                        tint = SteelGrayText,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // History logs
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Today's Discipline Logs",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = PremiumWhite
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SteelGrey800, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = null,
                            tint = SteelGrayText,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Discipline log is empty. Activate Deep Lock to record your focus sessions!",
                            fontSize = 12.sp,
                            color = SteelGrayText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        items(logs.take(10)) { logItem ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(SteelGrey800, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val icon = when (logItem.eventType) {
                        "LOCK_START" -> Pair(Icons.Default.HourglassTop, SteelCrimsonLight)
                        "LOCK_END" -> Pair(Icons.Default.Celebration, SteelEmerald)
                        "DISTRACTION" -> Pair(Icons.Default.ReportProblem, SteelAmber)
                        "CHALLENGE_COMPLETED" -> Pair(Icons.Default.Verified, SteelEmerald)
                        "CHALLENGE_FAILED" -> Pair(Icons.Default.Cancel, SteelCrimson)
                        else -> Pair(Icons.Default.Circle, SteelGrayText)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon.first,
                            contentDescription = null,
                            tint = icon.second,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = logItem.eventType,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = icon.second
                        )
                    }

                    Text(
                        text = sdf.format(Date(logItem.timestamp)),
                        fontSize = 11.sp,
                        color = SteelGrayText
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = logItem.details,
                    fontSize = 13.sp,
                    color = PremiumWhite,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun FocusStreakCard(streakDays: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SteelGrey800),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, SteelGrey700),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(SteelCrimson.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Streak",
                        tint = SteelCrimsonLight,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Daily Streak: $streakDays Days of Focus",
                        color = PremiumWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Unyielding willpower. Keep pushing to unlock dynamic VIP badges!",
                        color = SteelGrayText,
                        fontSize = 11.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .background(SteelCrimson.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "+50XP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = SteelCrimsonLight
                )
            }
        }
    }
}

// ======================= LOCKED / FOCUS SCREEN =======================
@Composable
fun LockedScreen(viewModel: FocusViewModel) {
    val activeSession by viewModel.activeSession.collectAsState()
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val activeChallengeType by viewModel.activeChallengeType.collectAsState()

    // Challenges state
    val mathProblem by viewModel.mathProblem.collectAsState()
    val mathAnswer by viewModel.mathAnswer.collectAsState()
    val mathCorrectCount by viewModel.mathCorrectCount.collectAsState()

    val squatCount by viewModel.squatCount.collectAsState()
    val totalSquatGoal = viewModel.totalSquatGoal

    val photoVerificationResult by viewModel.photoVerificationResult.collectAsState()
    val isScanningPhoto by viewModel.isScanningPhoto.collectAsState()

    val context = LocalContext.current

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        // Large Warning Symbol of Prison focus lock
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = SteelCrimsonLight,
            modifier = Modifier.size(54.dp)
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = "DEEP FOCUS LOCK ACTIVE",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = SteelCrimsonLight,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Large glowing countdown ring
        Box(
            modifier = Modifier
                .size(240.dp)
                .drawBehind {
                    drawCircle(
                        color = SteelGrey700,
                        radius = size.minDimension / 2,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12.dp.toPx())
                    )
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(SteelCrimson, SteelAmber, SteelCrimson)
                        ),
                        radius = size.minDimension / 2,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 12.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formattedTime,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black,
                    color = PremiumWhite,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "remaining time",
                    fontSize = 11.sp,
                    color = SteelGrayText,
                    fontWeight = FontWeight.Bold
                )
                
                activeSession?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(SteelCrimson.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Distractions: ${it.distractionCount} Times",
                            color = SteelCrimsonLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = SteelGrey800),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, SteelGrey700),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Need an Early Unlock?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = PremiumWhite,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "FocusLock AI requires you to complete one of the following challenges to bypass the screen and safeguard your willpower:",
                    fontSize = 12.sp,
                    color = SteelGrayText,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                if (activeChallengeType == "CHOOSE") {
                    // Challenges picker options
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ChallengeRowItem(
                            title = "Solve 5 Math Equations",
                            desc = "High-speed logic calculations",
                            icon = Icons.Default.Calculate,
                            onClick = { viewModel.selectChallenge("MATH") }
                        )

                        ChallengeRowItem(
                            title = "Physical Burst: 15 Squats",
                            desc = "Uses your device's accelerometer",
                            icon = Icons.Default.DirectionsRun,
                            onClick = { viewModel.selectChallenge("SQUAT") }
                        )

                        ChallengeRowItem(
                            title = "AI Study Photo Verification",
                            desc = "Capture physical study materials for AI review",
                            icon = Icons.Default.AutoAwesome,
                            onClick = { viewModel.selectChallenge("AI_PHOTO") }
                        )
                    }
                } else {
                    // Active Challenge Frame Render
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active challenge session...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SteelAmber
                        )
                        Button(
                            onClick = { viewModel.selectChallenge("CHOOSE") },
                            colors = ButtonDefaults.buttonColors(containerColor = SteelGrey700),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Change Challenge", fontSize = 11.sp, color = PremiumWhite)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    when (activeChallengeType) {
                        "MATH" -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Question ${mathCorrectCount + 1}/5:",
                                    color = SteelAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = mathProblem,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PremiumWhite
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = mathAnswer,
                                    onValueChange = { viewModel.mathAnswer.value = it },
                                    label = { Text("Your answer") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SteelCrimsonLight,
                                        unfocusedBorderColor = SteelGrey700,
                                        focusedLabelColor = SteelCrimsonLight,
                                        focusedTextColor = PremiumWhite,
                                        unfocusedTextColor = PremiumWhite
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = { viewModel.submitMathAnswer() },
                                    colors = ButtonDefaults.buttonColors(containerColor = SteelCrimson),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Submit Answer", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        "SQUAT" -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .background(SteelAmber.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$squatCount/$totalSquatGoal",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        color = SteelAmber
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Stand upright with your device held up. Lower your hips deeply for a squat, then stand straight back up!",
                                    fontSize = 12.sp,
                                    color = SteelGrayText,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = { viewModel.simulateSquat() },
                                    colors = ButtonDefaults.buttonColors(containerColor = SteelGrey700),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.DirectionsRun, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Simulate 1 Squat pulse (for emulator)", color = Color.White)
                                }
                            }
                        }

                        "AI_PHOTO" -> {
                            val cameraLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.TakePicturePreview()
                            ) { bitmap ->
                                if (bitmap != null) {
                                    val base64 = convertBitmapToBase64(bitmap)
                                    viewModel.scanStudyPhoto(base64)
                                }
                            }

                            val galleryLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.GetContent()
                            ) { uri ->
                                if (uri != null) {
                                    val base64 = convertUriToBase64(context, uri)
                                    if (base64 != null) {
                                        viewModel.scanStudyPhoto(base64)
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (photoVerificationResult == null) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = null,
                                        tint = SteelCrimsonLight,
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Capture or select a photo of written notes, handbook equations, or raw code to verify actual learning.",
                                        fontSize = 12.sp,
                                        color = SteelGrayText,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                photoVerificationResult?.let { res ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = if (res.startsWith("APPROVED")) SteelEmerald.copy(alpha = 0.15f)
                                                else if (res.startsWith("DENIED")) SteelCrimson.copy(alpha = 0.15f)
                                                else SteelGrey700,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (res.startsWith("APPROVED")) SteelEmerald
                                                else if (res.startsWith("DENIED")) SteelCrimson
                                                else Color.Transparent,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = res,
                                            fontSize = 13.sp,
                                            color = PremiumWhite,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }

                                if (isScanningPhoto) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    CircularProgressIndicator(color = SteelCrimsonLight)
                                    Text(
                                        text = "AI is scanning study artifacts, please wait...",
                                        fontSize = 11.sp,
                                        color = SteelAmber,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { cameraLauncher.launch() },
                                        colors = ButtonDefaults.buttonColors(containerColor = SteelCrimson),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Take Photo", color = Color.White)
                                    }

                                    Button(
                                        onClick = { galleryLauncher.launch("image/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = SteelGrey700),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pick Gallery", color = Color.White)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                // Quick simulation study paper bypass for ease of virtual container trials
                                Button(
                                    onClick = { 
                                        viewModel.scanStudyPhoto("STUDY_MOCK_DATA")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SteelGrey700.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Bypass Verification (Simulator)", color = SteelGrayText, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChallengeRowItem(
    title: String,
    desc: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SteelGrey700, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SteelCrimson.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = SteelCrimsonLight, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PremiumWhite)
            Text(text = desc, fontSize = 11.sp, color = SteelGrayText)
        }
        Icon(
            imageVector = Icons.Default.ArrowForwardIos,
            contentDescription = null,
            tint = SteelGrayText,
            modifier = Modifier.size(12.dp)
        )
    }
}

// ======================= AI COACH SCREEN =======================
@Composable
fun AiCoachScreen(viewModel: FocusViewModel) {
    val aiResponse by viewModel.aiCoachResponse.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo("HOME") },
                modifier = Modifier
                    .background(SteelGrey700, CircleShape)
                    .size(36.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PremiumWhite)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "AI Discipline Coach",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PremiumWhite
                )
                Text(
                    text = "PROCRASTINATION HABIT BEHAVIOR",
                    fontSize = 11.sp,
                    color = SteelAmber,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Coach Profile avatar Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SteelGrey800),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, SteelGrey700),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .background(SteelCrimson.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = SteelCrimsonLight,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = "Focus Lock Coach",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PremiumWhite
                )
                Text(
                    text = "Strict • Analytical • Action-driven",
                    fontSize = 11.sp,
                    color = SteelGrayText,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "The AI Coach scans your locked session events, calculates daily procrastination spikes, reviews focus durations, and outputs a highly optimized discipline daily timeline for tomorrow.",
                    fontSize = 12.sp,
                    color = PremiumWhite,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { viewModel.triggerCoachAnalysis() },
                    colors = ButtonDefaults.buttonColors(containerColor = SteelCrimson),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAiLoading
                ) {
                    Icon(Icons.Default.Timeline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate AI Coach Analysis", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Result visual render
        if (isAiLoading || aiResponse != null) {
            Text(
                text = "AI COACH ASSESSMENT",
                fontSize = 12.sp,
                color = SteelGrayText,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = SteelGrey800),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SteelAmber.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 50.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (isAiLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = SteelCrimsonLight, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Analyzing procrastination habits...", color = SteelGrayText, fontSize = 13.sp)
                        }
                    }

                    aiResponse?.let { text ->
                        Text(
                            text = text,
                            fontSize = 13.sp,
                            color = PremiumWhite,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ======================= SETTINGS SCREEN =======================
@Composable
fun SettingsScreen(
    viewModel: FocusViewModel,
    hasUsage: Boolean,
    hasOverlay: Boolean
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo("HOME") },
                modifier = Modifier
                    .background(SteelGrey700, CircleShape)
                    .size(36.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PremiumWhite)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "System Shield",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PremiumWhite
                )
                Text(
                    text = "REQUIRED DISCIPLINE PERMISSIONS",
                    fontSize = 11.sp,
                    color = SteelGrayText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = SteelGrey800),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, SteelGrey700),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Why are these permissions required?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PremiumWhite
                )
                Text(
                    text = "1. Usage Access: Monitors when entertainment/social apps are launched while your lock session is active.\n2. Overlay Permission: Instantly throws the focus shield overlay on top of any restricted, addictive apps.",
                    fontSize = 12.sp,
                    color = SteelGrayText,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                // Permission 1 row
                PermissionStatusRow(
                    title = "Usage Access",
                    desc = "Tracks active foreground apps",
                    isGranted = hasUsage,
                    onGrantClick = { viewModel.openUsageSettings(context) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Permission 2 row
                PermissionStatusRow(
                    title = "Display over other apps",
                    desc = "Launches the focus block shield",
                    isGranted = hasOverlay,
                    onGrantClick = { viewModel.openOverlaySettings(context) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Danger zone clear database
        Card(
            colors = CardDefaults.cardColors(containerColor = SteelGrey800),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, SteelCrimson.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Danger Zone",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = SteelCrimsonLight
                )
                Text(
                    text = "Clear your entire Room physical database, wiping focus session history and timeline logs clean.",
                    fontSize = 12.sp,
                    color = SteelGrayText,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Button(
                    onClick = { 
                        viewModel.clearAllData()
                        Toast.makeText(context, "Database cleared successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SteelCrimson),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Database", color = Color.White)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "FocusLock AI v1.0.0 Stable\nPowered by Jetpack Compose & SQLite",
            fontSize = 11.sp,
            color = SteelGrayText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    desc: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SteelGrey700, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PremiumWhite)
            Text(text = desc, fontSize = 11.sp, color = SteelGrayText)
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isGranted) "✓ Active & Protected" else "✗ Blocked",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGranted) SteelEmerald else SteelCrimsonLight
            )
        }
        
        Button(
            onClick = onGrantClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGranted) SteelGrey900 else SteelCrimson
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isGranted) "Granted" else "Grant",
                fontSize = 12.sp,
                color = PremiumWhite,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Image conversion helper functions
fun convertBitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}

fun convertUriToBase64(context: Context, uri: Uri): String? {
    return try {
        val inputStream: java.io.InputStream? = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        if (bitmap != null) {
            convertBitmapToBase64(bitmap)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
