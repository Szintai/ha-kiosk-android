package hu.szintai.hakiosk.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.szintai.hakiosk.ui.theme.Palette

/** Small uppercase label above a group, like the dashboard's section headings. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Palette.Mute,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 6.dp, top = 28.dp, bottom = 10.dp),
    )
}

/** Rounded grouped card; rows are separated by hairlines. */
@Composable
fun GroupCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Palette.Card)
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) { content() }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
}

@Composable
private fun RowShell(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = Palette.Mute, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowShell(title, subtitle) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Palette.Orange,
                checkedBorderColor = Palette.Orange,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Palette.Soft,
                uncheckedBorderColor = Palette.Soft,
            ),
        )
    }
}

/** − value + control for bounded numbers. */
@Composable
fun StepperRow(
    title: String,
    subtitle: String? = null,
    value: Int,
    range: IntRange,
    step: Int,
    unit: String,
    onChange: (Int) -> Unit,
) {
    RowShell(title, subtitle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton("−", enabled = value > range.first) { onChange((value - step).coerceAtLeast(range.first)) }
            Text(
                "$value $unit",
                color = Palette.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(84.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            RoundButton("+", enabled = value < range.last) { onChange((value + step).coerceAtMost(range.last)) }
        }
    }
}

@Composable
private fun RoundButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Palette.Soft)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Palette.Ink else Palette.Mute, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

/** iOS-style segmented control. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Soft)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) Palette.Card else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) Palette.Ink else Palette.Mute,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun HintText(text: String, color: Color = Palette.Mute) {
    Text(text, color = color, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp, bottom = 10.dp))
}

/** Label above a filled, border-less text field. */
@Composable
fun FieldRow(
    label: String,
    value: String,
    number: Boolean = false,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    var reveal by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Palette.Mute, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (secret) {
                Text(
                    if (reveal) "Elrejt" else "Mutat",
                    color = Palette.Orange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { reveal = !reveal },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (secret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Uri),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Palette.Soft,
                unfocusedContainerColor = Palette.Soft,
                focusedTextColor = Palette.Ink,
                unfocusedTextColor = Palette.Ink,
                cursorColor = Palette.Orange,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun PillButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) Palette.Orange else Palette.Soft)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) Color.Black else Palette.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
