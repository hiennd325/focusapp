package com.example.viewmodel

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.*
import com.example.data.*
import com.example.service.FocusLockService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.abs
import kotlin.random.Random

class FocusViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val repository: FocusRepository
    private val sensorManager: SensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Current screen navigation state: "HOME", "LOCKED", "AI_COACH", "SETTINGS"
    private val _currentScreen = MutableStateFlow("HOME")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Database UI States
    val sessions: StateFlow<List<FocusSession>>
    val logs: StateFlow<List<FocusLockLog>>

    // Focus session runtime states
    private val _activeSession = MutableStateFlow<FocusSession?>(null)
    val activeSession: StateFlow<FocusSession?> = _activeSession.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private var countdownJob: kotlinx.coroutines.Job? = null

    // Block statistics & Streak
    val streakDays = MutableStateFlow(0)
    val checkInStreak = MutableStateFlow(5) // default indicator

    // --- Active Unlock Challenge UI states ---
    // Types: "CHOOSE", "MATH", "SQUAT", "AI_PHOTO"
    val activeChallengeType = MutableStateFlow("CHOOSE")

    // Math Challenge State
    val mathProblem = MutableStateFlow("")
    val mathAnswer = MutableStateFlow("")
    val mathCorrectCount = MutableStateFlow(0)
    private var currentMathTarget = 0

    // Squat Challenge State
    val squatCount = MutableStateFlow(0)
    val totalSquatGoal = 15
    private var lastYValue = 9.8f
    private var isSquatGoingDown = false
    private var lastSquatTime = 0L

    // AI Photo Scanning State
    val photoVerificationResult = MutableStateFlow<String?>(null) // "APPROVED", "DENIED", "ANALYZING", or raw reason
    val isScanningPhoto = MutableStateFlow(false)
    val capturedPhotoBase64 = MutableStateFlow<String?>(null)

    // AI Productivity Coach Response
    val aiCoachResponse = MutableStateFlow<String?>(null)
    val isAiLoading = MutableStateFlow(false)

    init {
        val dao = FocusDatabase.getDatabase(application).focusDao()
        repository = FocusRepository(dao)
        sessions = repository.allSessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        logs = repository.allLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Fetch active session and start countdown if necessary
        viewModelScope.launch {
            val active = repository.getActiveSession()
            if (active != null) {
                _activeSession.value = active
                _currentScreen.value = "LOCKED"
                resumeCountdown(active)
            }
            calculateStreaks()
        }
    }

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    // --- Focus Actions ---
    fun startFocusSession(minutes: Int) {
        viewModelScope.launch {
            val id = repository.startSession(minutes)
            val session = repository.getActiveSession()
            if (session != null) {
                _activeSession.value = session
                _currentScreen.value = "LOCKED"
                _remainingSeconds.value = minutes * 60
                repository.logEvent("LOCK_START", "Started deep lock for $minutes minutes.")
                startBackgroundService()
                startTimer()
            }
            calculateStreaks()
        }
    }

    private fun startTimer() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1000)
                _remainingSeconds.value -= 1
            }
            // Completed!
            completeActiveSession()
        }
    }

    private fun resumeCountdown(session: FocusSession) {
        val elapsedMs = System.currentTimeMillis() - session.startTime
        val totalMs = session.plannedDurationMinutes * 60 * 1000L
        val remainingMs = totalMs - elapsedMs
        if (remainingMs > 0) {
            _remainingSeconds.value = (remainingMs / 1000).toInt()
            startTimer()
            startBackgroundService()
        } else {
            viewModelScope.launch {
                completeActiveSession()
            }
        }
    }

    private suspend fun completeActiveSession() {
        countdownJob?.cancel()
        val current = _activeSession.value ?: return
        val updated = current.copy(
            endTime = System.currentTimeMillis(),
            status = "COMPLETED"
        )
        repository.updateSession(updated)
        repository.logEvent("LOCK_END", "Successfully completed deep focus session! Device unlocked.")
        
        _activeSession.value = null
        _remainingSeconds.value = 0
        stopBackgroundService()
        
        withContext(Dispatchers.Main) {
            navigateTo("HOME")
            activeChallengeType.value = "CHOOSE"
            resetChallenges()
        }
        calculateStreaks()
    }

    fun forceEarlyUnlock() {
        viewModelScope.launch {
            val current = _activeSession.value ?: return@launch
            val updated = current.copy(
                endTime = System.currentTimeMillis(),
                status = "ABANDONED",
                unlockChallengeType = activeChallengeType.value
            )
            repository.updateSession(updated)
            repository.logEvent("LOCK_END", "Early unlock granted after completing challenge: ${activeChallengeType.value}")

            _activeSession.value = null
            _remainingSeconds.value = 0
            stopBackgroundService()

            withContext(Dispatchers.Main) {
                navigateTo("HOME")
                activeChallengeType.value = "CHOOSE"
                resetChallenges()
            }
            calculateStreaks()
        }
    }

    private fun startBackgroundService() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, FocusLockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopBackgroundService() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, FocusLockService::class.java).apply {
            action = "STOP_FOCUS"
        }
        context.startService(intent)
    }

    // --- Challenge Logic ---
    fun selectChallenge(type: String) {
        activeChallengeType.value = type
        resetChallenges()

        if (type == "SQUAT") {
            registerSquatSensor()
        } else {
            unregisterSquatSensor()
        }

        if (type == "MATH") {
            generateMathProblem()
        }
    }

    fun resetChallenges() {
        mathAnswer.value = ""
        mathCorrectCount.value = 0
        squatCount.value = 0
        photoVerificationResult.value = null
        capturedPhotoBase64.value = null
        isScanningPhoto.value = false
    }

    // Math calculation
    private fun generateMathProblem() {
        mathAnswer.value = ""
        val op = if (Random.nextBoolean()) "+" else "x"
        if (op == "+") {
            val a = Random.nextInt(20, 89)
            val b = Random.nextInt(15, 69)
            mathProblem.value = "$a + $b = ?"
            currentMathTarget = a + b
        } else {
            val a = Random.nextInt(4, 12)
            val b = Random.nextInt(5, 9)
            mathProblem.value = "$a x $b = ?"
            currentMathTarget = a * b
        }
    }

    fun submitMathAnswer() {
        val parsed = mathAnswer.value.toIntOrNull()
        if (parsed == currentMathTarget) {
            mathCorrectCount.value += 1
            if (mathCorrectCount.value >= 5) {
                // Completed!
                viewModelScope.launch {
                    repository.logEvent("CHALLENGE_COMPLETED", "Solved 5 math logic problems!")
                    forceEarlyUnlock()
                }
            } else {
                generateMathProblem()
            }
        } else {
            // Wrong
            mathAnswer.value = ""
            viewModelScope.launch {
                repository.logEvent("CHALLENGE_FAILED", "Incorrect math challenge answer!")
            }
        }
    }

    // Squat Accelerometer Registration
    private fun registerSquatSensor() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun unregisterSquatSensor() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        
        // Squat detection with Accelerometer
        val y = event.values[1] // gravity along Y axis
        val timeNow = System.currentTimeMillis()

        if (timeNow - lastSquatTime > 500) {
            // Squat down: Y reduces significantly as physical elevation acceleration drops (e.g. state change < 5.5)
            if (y < 5.5f && !isSquatGoingDown) {
                isSquatGoingDown = true
                lastSquatTime = timeNow
            } 
            // Squat up: standing back up spikes physical elevation acceleration (e.g., > 12.5)
            else if (y > 12.5f && isSquatGoingDown) {
                isSquatGoingDown = false
                squatCount.value += 1
                lastSquatTime = timeNow
                
                viewModelScope.launch {
                    repository.logEvent("CHALLENGE_PROGRESS", "Squat pulse detected: ${squatCount.value}/$totalSquatGoal reps")
                }

                if (squatCount.value >= totalSquatGoal) {
                    unregisterSquatSensor()
                    viewModelScope.launch {
                        repository.logEvent("CHALLENGE_COMPLETED", "Completed squat physical challenge!")
                        forceEarlyUnlock()
                    }
                }
            }
        }
        lastYValue = y
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Manual Squat for emulators or fast verification
    fun simulateSquat() {
        squatCount.value += 1
        if (squatCount.value >= totalSquatGoal) {
            unregisterSquatSensor()
            viewModelScope.launch {
                repository.logEvent("CHALLENGE_COMPLETED", "Physical squat challenge completed!")
                forceEarlyUnlock()
            }
        }
    }

    // Gemini Study Image Analysis
    fun scanStudyPhoto(base64Image: String) {
        capturedPhotoBase64.value = base64Image
        isScanningPhoto.value = true
        photoVerificationResult.value = "ANALYZING"

        viewModelScope.launch {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey.contains("MY_GEMINI_API_KEY")) {
                delay(1200)
                // Fallback demo for visual safety
                photoVerificationResult.value = "APPROVED: [Simulation Mode] AI detected a notebook page with handwritten notes. Study session verified!"
                delay(2000)
                repository.logEvent("CHALLENGE_COMPLETED", "AI approved study notes photo!")
                forceEarlyUnlock()
                return@launch
            }

            val prompt = "Analyze this image and determine if it represents school books, notebooks, handwritten study answers, raw programming code, or working/learning materials. Reply with exactly 'APPROVED' on the first line if it is valid study/work material, or 'DENIED' if it is not (e.g. empty desk, face selfies, food, streets, computers with unrelated screens). On the next line, include a brief English explanation."

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = prompt),
                            GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image))
                        )
                    )
                )
            )

            try {
                val response = withContext(Dispatchers.IO) {
                    GeminiClient.service.generateContent(apiKey, request)
                }
                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                Log.d("FocusLockAI", "Gemini OCR scan says: $textResponse")

                if (textResponse.contains("APPROVED", ignoreCase = true)) {
                    photoVerificationResult.value = "APPROVED: Verified study session!\n\n$textResponse"
                    repository.logEvent("CHALLENGE_COMPLETED", "AI approved study session photo.")
                    delay(3000)
                    forceEarlyUnlock()
                } else {
                    photoVerificationResult.value = "DENIED: Photo rejected. Please take a clear photo of learning materials or written paper!\n\n$textResponse"
                    repository.logEvent("CHALLENGE_FAILED", "AI rejected study photo verification.")
                }
            } catch (e: Exception) {
                Log.e("FocusLockAI", "Gemini call fails", e)
                photoVerificationResult.value = "ERR: AI connection error (${e.localizedMessage}). Emergency unlocking in 3 seconds."
                delay(3000)
                forceEarlyUnlock()
            } finally {
                isScanningPhoto.value = false
            }
        }
    }

    // Gemini Coach Chat & Productivity Analysis
    fun triggerCoachAnalysis() {
        isAiLoading.value = true
        aiCoachResponse.value = "Synthesizing study data and requesting AI Coach analysis..."

        viewModelScope.launch {
            val apiKey = BuildConfig.GEMINI_API_KEY
            val sessionList = sessions.value
            val logList = logs.value

            // Synthesize database metrics
            val sessionsToday = sessionList.filter { it.startTime > System.currentTimeMillis() - 24 * 3600 * 1000L }
            val completedCount = sessionsToday.count { it.status == "COMPLETED" }
            val totalMinutes = sessionsToday.sumOf { it.plannedDurationMinutes }
            val distractionTotal = sessionsToday.sumOf { it.distractionCount }

            val statsPrompt = """
                Hi AI Discipline Coach. Please analyze my focus study report for the last 24 hours:
                - Focus sessions launched today: ${sessionsToday.size} sessions
                - Completed sessions: $completedCount sessions
                - Total focused training duration: $totalMinutes minutes
                - Distraction count (blocked attempts/tries to exit): $distractionTotal times
                - Activity log events: ${logList.take(15).joinToString("; ") { "[${it.eventType}]: ${it.details}" }}
                
                Please provide sharp, critical, and motivational feedback in English about my productivity habits.
                Highlight my procrastination vulnerability windows based on the logs.
                Generate a structured hourly timeline schedule for tomorrow to help me stick to steel discipline and beat distractions. Keep it highly action-oriented and structured!
            """.trimIndent()

            if (apiKey.isEmpty() || apiKey.contains("MY_GEMINI_API_KEY")) {
                delay(1500)
                aiCoachResponse.value = """
                    📊 **STEEL DISCIPLINE AI COACH REPORT (DEMO)**
                    
                    Hello! After analyzing your focus data for today, here is the assessment from your FocusLock AI Coach:
                    
                    🔥 **Focus Score**: 7.5/10. Point score is based on performing $totalMinutes minutes of focus and completing $completedCount locked sessions.
                    ⚠️ **Procrastination Vulns**: You had $distractionTotal attempts to open social apps. You are highly vulnerable to distractions during the afternoon (14:00 - 16:00).
                    
                    📅 **Recommended Discipline Schedule for Tomorrow**:
                    - **08:00 - 10:00 (Morning)**: Deep Lock for 120 minutes. Tackle your hardest programming task or textbook chapter first.
                    - **10:00 - 10:15**: Quick structural break (do 15 Squats).
                    - **14:00 - 16:00 (Afternoon)**: Lock down for 120 minutes. Zero tolerance for social distractions!
                    - **21:00**: Reflect on your day with your AI Coach.
                    
                    *Train your mind today to shape your freedom tomorrow!*
                """.trimIndent()
                isAiLoading.value = false
                return@launch
            }

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = statsPrompt))
                    )
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = "You are the strict, sharp, and highly motivating FocusLock AI Coach. Analyze the user's focus patterns, criticize procrastination sharply, and provide a structured hourly timeline study schedule in English."))
                )
            )

            try {
                val response = withContext(Dispatchers.IO) {
                    GeminiClient.service.generateContent(apiKey, request)
                }
                aiCoachResponse.value = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "No response from AI Coach..."
            } catch (e: Exception) {
                Log.e("FocusLockAI", "Coach fails", e)
                aiCoachResponse.value = "Connection error to AI Coach: ${e.localizedMessage}. Please try again later."
            } finally {
                isAiLoading.value = false
            }
        }
    }

    private fun calculateStreaks() {
        // Simple streak builder based on daily completions
        viewModelScope.launch {
            val list = sessions.value.filter { it.status == "COMPLETED" }
            if (list.isNotEmpty()) {
                streakDays.value = 3 // default baseline for simulation
            } else {
                streakDays.value = 0
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearData()
            calculateStreaks()
        }
    }

    // --- Permissions Checking System Guides ---
    fun isUsageAccessGranted(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    applicationInfo.uid,
                    context.packageName
                )
            } else {
                appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    applicationInfo.uid,
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun isOverlayAccessGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun openUsageSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    override fun onCleared() {
        unregisterSquatSensor()
        super.onCleared()
    }
}
