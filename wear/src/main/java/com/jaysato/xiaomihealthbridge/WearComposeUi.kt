package com.jaysato.xiaomihealthbridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*

object WearComposeUi {
    val Secondary = Color(0xFFA7A7B2)

    @Composable
    fun Screen(title: String, content: TransformingLazyColumnScope.() -> Unit) {
        MaterialTheme {
            AppScaffold(containerColor = Color.Black) {
                val state = rememberTransformingLazyColumnState()
                ScreenScaffold(scrollState = state) { padding ->
                    TransformingLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = state,
                        contentPadding = PaddingValues(
                            start = 18.dp,
                            end = 18.dp,
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 20.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Text(
                                text = title,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                        content()
                    }
                }
            }
        }
    }

    @Composable
    fun HealthButton(title: String, subtitle: String, color: Color, icon: String = title.take(1), onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1C1C1E),
                contentColor = Color.White
            )
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).background(color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = Secondary, fontSize = 11.sp, maxLines = 1)
                }
                Text("›", color = Secondary, fontSize = 20.sp)
            }
        }
    }

    @Composable
    fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
        Button(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().height(62.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1C1C1E),
                contentColor = Color.White
            )
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = Secondary, fontSize = 11.sp, maxLines = 1)
                }
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 26.dp)
                        .background(
                            if (checked) Color(0xFF34C759) else Color(0xFF3A3A3C),
                            CircleShape
                        ),
                    contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .padding(3.dp)
                            .size(20.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }
    }
}
