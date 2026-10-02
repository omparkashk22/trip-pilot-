package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAppColors

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isNumeric: Boolean = false,
    unitText: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    testTag: String = "app_text_field"
) {
    val colors = LocalAppColors.current

    val effectiveKeyboardOptions = keyboardOptions ?: if (isNumeric) {
        KeyboardOptions(keyboardType = KeyboardType.Decimal)
    } else {
        KeyboardOptions.Default
    }

    val onValueChangeFiltered: (String) -> Unit = { input ->
        if (isNumeric) {
            val filtered = input.filter { it.isDigit() || it == '.' }
            if (filtered.count { it == '.' } <= 1) {
                onValueChange(filtered)
            }
        } else {
            onValueChange(input)
        }
    }

    val effectiveTrailingIcon: @Composable (() -> Unit)? = when {
        trailingIcon != null -> trailingIcon
        unitText != null -> {
            {
                Text(
                    text = unitText,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
        else -> null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChangeFiltered,
            label = {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            },
            placeholder = {
                Text(
                    text = placeholder,
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            isError = isError,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            keyboardOptions = effectiveKeyboardOptions,
            leadingIcon = leadingIcon,
            trailingIcon = effectiveTrailingIcon,
            textStyle = TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.text
            ),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.hairline,
                focusedLabelColor = colors.accent,
                unfocusedLabelColor = colors.textSecondary,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                cursorColor = colors.accent,
                errorBorderColor = colors.danger,
                errorLabelColor = colors.danger
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(testTag)
        )
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = colors.danger,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }
    }
}
