package com.example.myapplication

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private lateinit var voiceAssistant: VoiceAssistant
    private var dynamicSmsReceiver: SmsReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceAssistant = VoiceAssistant(this)

        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest.toTypedArray(),
            101
        )

        registerDynamicSmsReceiver()

        val initialSms = intent.getStringExtra("SCAM_TEXT") ?: ""
        val isCallAlert = intent.getBooleanExtra("IS_CALL_ALERT", false)
        val callNumber = intent.getStringExtra("CALL_NUMBER") ?: ""

        setContent {
            var showWelcome by remember { mutableStateOf(true) }

            if (showWelcome) {
                WelcomeScreen(onGetStarted = { showWelcome = false })
            } else {
                SurakshitScreen(voiceAssistant, initialSms, isCallAlert, callNumber)
            }
        }
    }

    private fun registerDynamicSmsReceiver() {
        try {
            val receiver = SmsReceiver()
            dynamicSmsReceiver = receiver

            val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
                priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            }

            ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newSms = intent.getStringExtra("SCAM_TEXT") ?: ""
        if (newSms.isNotBlank()) {
            setContent {
                SurakshitScreen(voiceAssistant, newSms, false, "")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceAssistant.shutdown()
        dynamicSmsReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.size(96.dp),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "🛡️", fontSize = 48.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Surakshit AI",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Welcome in Surakshit AI",
                fontSize = 18.sp,
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Surakshit AI provides real-time call monitoring.Instantly evaluate messages, URLs, and QR codes by pasting them directly into the platform for proactive digital protection.",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onGetStarted,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Get Started",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurakshitScreen(
    voiceAssistant: VoiceAssistant,
    initialText: String,
    isCallAlert: Boolean,
    callNumber: String
) {
    val context = LocalContext.current
    var selectedLang by remember { mutableStateOf("hi") }
    var inputType by remember { mutableStateOf(if (isCallAlert) FraudType.CALL else FraudType.SMS) }
    var inputText by remember { mutableStateOf(initialText) }
    var analysisResult by remember { mutableStateOf<AnalysisResult?>(null) }

    var dropdownExpanded by remember { mutableStateOf(false) }

    var showClipboardDialog by remember { mutableStateOf(false) }
    var pendingClipboardText by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    val allLanguages = AppLanguage.entries.map { it.code to it.displayName }

    val qrImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            try {
                val image = InputImage.fromFilePath(context, imageUri)
                val scanner = BarcodeScanning.getClient()
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val qrRawValue = barcodes.firstOrNull()?.rawValue ?: ""
                        if (qrRawValue.isNotBlank()) {
                            inputText = qrRawValue
                            inputType = FraudType.QR_CODE
                            val res = ScamAnalyzer.analyze(qrRawValue, selectedLang, FraudType.QR_CODE)
                            analysisResult = res
                            voiceAssistant.speakWarning(res.voiceAlert, selectedLang)
                        } else {
                            inputText = "No QR code found in selected photo. If fraud is suspected, call Helpline 1930."
                        }
                    }
                    .addOnFailureListener {
                        inputText = "Error analyzing image. For cybercrime assistance, call Helpline 1930."
                    }
            } catch (e: Exception) {
                inputText = "Invalid image file. If scammed, report to Cybercrime Helpline 1930."
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
            if (spokenText.isNotBlank()) {
                inputText = spokenText
                val res = ScamAnalyzer.analyze(spokenText, selectedLang, inputType)
                analysisResult = res
                voiceAssistant.speakWarning(res.voiceAlert, selectedLang)
            }
        }
    }

    LaunchedEffect(initialText) {
        if (initialText.isNotBlank()) {
            val res = ScamAnalyzer.analyze(initialText, selectedLang, FraudType.SMS)
            analysisResult = res
            voiceAssistant.speakWarning(res.voiceAlert, selectedLang)
        }
    }

    LaunchedEffect(Unit) {
        SmsRepository.smsFlow.collectLatest { smsData ->
            if (smsData.message.isNotBlank()) {
                inputText = smsData.message
                inputType = FraudType.SMS
                val result = ScamAnalyzer.analyze(smsData.message, selectedLang, FraudType.SMS)
                analysisResult = result
                voiceAssistant.speakWarning(result.voiceAlert, selectedLang)
            }
        }
    }

    if (showClipboardDialog) {
        AlertDialog(
            onDismissRequest = { showClipboardDialog = false },
            title = { Text("📋 Open Clipboard", fontWeight = FontWeight.Bold) },
            text = {
                Text("Do you want to paste the copied content from your clipboard into Surakshit AI?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClipboardDialog = false
                        if (pendingClipboardText.isNotBlank()) {
                            inputText = pendingClipboardText
                            val result = ScamAnalyzer.analyze(pendingClipboardText, selectedLang, inputType)
                            analysisResult = result
                            voiceAssistant.speakWarning(result.voiceAlert, selectedLang)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                ) {
                    Text("Allow & Paste", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClipboardDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FC))
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🛡️ Surakshit AI",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B5E20)
        )
        Text(
            text = "🔒 Real-Time Multi-Language Scam Shield",
            fontSize = 11.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isCallAlert) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📞 Incoming Call Detected: $callNumber",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        text = "If caller demands money or claims to be Police/CBI, speak or paste conversation below!",
                        fontSize = 12.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilterChip(
                selected = inputType == FraudType.SMS,
                onClick = { inputType = FraudType.SMS },
                label = { Text("💬 SMS", fontSize = 11.sp) }
            )
            FilterChip(
                selected = inputType == FraudType.CALL,
                onClick = { inputType = FraudType.CALL },
                label = { Text("📞 Call", fontSize = 11.sp) }
            )
            FilterChip(
                selected = inputType == FraudType.URL,
                onClick = { inputType = FraudType.URL },
                label = { Text("🌐 URL", fontSize = 11.sp) }
            )
            FilterChip(
                selected = inputType == FraudType.QR_CODE,
                onClick = { inputType = FraudType.QR_CODE },
                label = { Text("📷 QR Code", fontSize = 11.sp) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Select Language / भाषा चुनें:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = !dropdownExpanded },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            val selectedDisplayName = allLanguages.firstOrNull { it.first == selectedLang }?.second ?: "Select Language"

            OutlinedTextField(
                value = selectedDisplayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                allLanguages.forEach { (code, name) ->
                    DropdownMenuItem(
                        text = { Text(name, fontSize = 13.sp) },
                        onClick = {
                            selectedLang = code
                            dropdownExpanded = false
                            if (inputText.isNotBlank()) {
                                val res = ScamAnalyzer.analyze(inputText, code, inputType)
                                analysisResult = res
                                voiceAssistant.speakWarning(res.voiceAlert, code)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = {
                Text(
                    when (inputType) {
                        FraudType.SMS -> "Paste SMS / Message Here"
                        FraudType.CALL -> "Speak or Paste Call Conversation"
                        FraudType.URL -> "Paste Link / URL Here"
                        FraudType.QR_CODE -> "Select QR Code Photo Below"
                    }
                )
            },
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (inputType != FraudType.QR_CODE) {
                Button(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            if (clipboard.hasPrimaryClip()) {
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    pendingClipboardText = clip.getItemAt(0).coerceToText(context).toString()
                                    showClipboardDialog = true
                                }
                            }
                        } catch (e: Exception) {
                            pendingClipboardText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    Text("📋 Paste SMS", fontSize = 12.sp)
                }
            }

            Button(
                onClick = {
                    inputText = ""
                    analysisResult = null
                    voiceAssistant.stop()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF757575)),
                modifier = Modifier.weight(if (inputType == FraudType.QR_CODE) 2f else 1f).padding(start = 4.dp)
            ) {
                Text("🔄 Refresh", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (inputType == FraudType.QR_CODE) {
            Button(
                onClick = { qrImagePickerLauncher.launch("image/*") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🖼️ Select QR Photo from Gallery", fontSize = 14.sp)
            }
        } else {
            Button(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLang)
                    }
                    try {
                        speechLauncher.launch(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (inputType) {
                        FraudType.CALL -> "🎙️ Voice Input (Speak Call Transcript)"
                        FraudType.SMS -> "🎙️ Voice Input (Speak SMS Message)"
                        FraudType.URL -> "🎙️ Voice Input (Speak Web URL)"
                        else -> "🎙️ Voice Input"
                    },
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val result = ScamAnalyzer.analyze(inputText, selectedLang, inputType)
                analysisResult = result
                voiceAssistant.speakWarning(result.voiceAlert, selectedLang)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))
        ) {
            Text("🔍 Analyze For Fraud", fontSize = 16.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        analysisResult?.let { result ->
            val isDangerous = result.riskLevel == RiskLevel.DANGEROUS
            val isSuspicious = result.riskLevel == RiskLevel.SUSPICIOUS

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isDangerous -> Color(0xFFFFEBEE)
                        isSuspicious -> Color(0xFFFFF3E0)
                        else -> Color(0xFFE8F5E9)
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = result.headline,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isDangerous -> Color(0xFFC62828)
                            isSuspicious -> Color(0xFFE65100)
                            else -> Color(0xFF2E7D32)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = result.explanation, fontSize = 14.sp, color = Color.Black)

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "👉 " + result.advice, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1565C0))

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                voiceAssistant.speakWarning(result.voiceAlert, selectedLang)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        ) {
                            Text("🔊 Listen Alert", fontSize = 13.sp)
                        }

                        Button(
                            onClick = { voiceAssistant.stop() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        ) {
                            Text("⏹️ Stop Voice", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}