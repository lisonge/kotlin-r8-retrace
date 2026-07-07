package li.songe.retrace.sample

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.retrace.Retrace
import li.songe.retrace.RetraceConfig
import li.songe.retrace.RetraceDefaults
import li.songe.retrace.RetraceException
import java.io.File

public fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "R8 Retrace Sample",
        ) {
            MaterialTheme(
                colorScheme =
                    lightColorScheme(
                        primary = Color(0xFF315F5C),
                        secondary = Color(0xFF765C2B),
                        surface = Color(0xFFF7F8F5),
                        surfaceVariant = Color(0xFFE2E7E1),
                        background = Color(0xFFF0F3EF),
                        onPrimary = Color.White,
                        onSurface = Color(0xFF17201E),
                    ),
            ) {
                RetraceSampleApp()
            }
        }
    }
}

@Composable
private fun RetraceSampleApp() {
    var mappingPath by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf(RetraceDefaults.DEFAULT_REGEX) }
    var verbose by remember { mutableStateOf(false) }
    var stackTrace by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val scrollState = rememberScrollState()
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "R8 Retrace",
                    style = MaterialTheme.typography.headlineMedium,
                )
                OutlinedTextField(
                    value = mappingPath,
                    onValueChange = { mappingPath = it },
                    label = { Text("mapping 文件路径") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ConfigPanel(
                    regex = regex,
                    onRegexChange = { regex = it },
                    verbose = verbose,
                    onVerboseChange = { verbose = it },
                )
                OutlinedTextField(
                    value = stackTrace,
                    onValueChange = { stackTrace = it },
                    label = { Text("报错堆栈文本") },
                    minLines = 8,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        loading = true
                        output = ""
                        scope.launch {
                            output =
                                withContext(Dispatchers.IO) {
                                    convert(
                                        mappingPath = mappingPath,
                                        regex = regex,
                                        verbose = verbose,
                                        stackTrace = stackTrace,
                                    )
                                }
                            loading = false
                        }
                    },
                    enabled = !loading && mappingPath.isNotBlank() && stackTrace.isNotBlank(),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (loading) "转换中" else "转换")
                }
                OutlinedTextField(
                    value = output,
                    onValueChange = { output = it },
                    label = { Text("输出") },
                    minLines = 10,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                )
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ConfigPanel(
    regex: String,
    onRegexChange: (String) -> Unit,
    verbose: Boolean,
    onVerboseChange: (Boolean) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LabeledCheckbox(
                checked = verbose,
                onCheckedChange = onVerboseChange,
                label = "verbose",
            )
        }
        OutlinedTextField(
            value = regex,
            onValueChange = onRegexChange,
            label = { Text("regex") },
            minLines = 2,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LabeledCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(56.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        SelectionContainer {
            Text(label)
        }
    }
}

private fun convert(
    mappingPath: String,
    regex: String,
    verbose: Boolean,
    stackTrace: String,
): String =
    runCatching {
        val mappingFile = File(mappingPath.trim())
        require(mappingFile.isFile) { "mapping 文件不存在: ${mappingFile.path}" }
        val config =
            RetraceConfig(
                regex = regex.ifBlank { RetraceDefaults.DEFAULT_REGEX },
                verbose = verbose,
            )
        Retrace.retrace(
            mapping = mappingFile.readText(),
            stackTrace = stackTrace,
            config = config,
        )
    }.getOrElse { error ->
        buildErrorOutput(error)
    }

private fun buildErrorOutput(error: Throwable): String =
    buildString {
        appendLine("${error::class.simpleName}: ${error.message.orEmpty()}")
        if (error is RetraceException && error.diagnostics.isNotEmpty()) {
            appendLine()
            appendLine("diagnostics:")
            error.diagnostics.forEach { diagnostic ->
                appendLine("${diagnostic.severity} line ${diagnostic.lineNumber}: ${diagnostic.message}")
            }
        }
    }.trimEnd()
