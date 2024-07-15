package com.looper.benchsuite.ui.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looper.android.support.util.AppUtils
import com.looper.android.support.util.IntentUtils
import com.looper.benchsuite.R
import com.looper.benchsuite.ui.theme.AppTheme

@Composable
fun AboutDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { onDismissRequest() },
        confirmButton = {},
        text = {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = stringResource(R.string.app_logo),
                    modifier = Modifier
                        .size(100.dp)
                        .padding(bottom = 16.dp)
                        .scale(1.5F),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "v" + AppUtils.getVersionName(
                        context
                    ), fontSize = 16.sp, color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                ButtonItem(
                    title = stringResource(R.string.more_apps_on_play_store),
                    onClick = {
                        IntentUtils.openURL(
                            context,
                            "https://play.google.com/store/apps/dev?id=6115418187591502860"
                        )
                    })
                ButtonItem(
                    title = stringResource(R.string.release_channel),
                    onClick = {
                        IntentUtils.openURL(
                            context,
                            "https://telegram.me/loopprojects"
                        )
                    })
                ButtonItem(
                    title = stringResource(R.string.credits),
                    onClick = {
                        IntentUtils.openURL(
                            context,
                            "https://github.com/iamlooper/BenchSuite/tree/main#credits-"
                        )
                    })
                ButtonItem(
                    title = stringResource(R.string.licenses),
                    onClick = {
                        IntentUtils.openURL(
                            context,
                            "https://github.com/iamlooper/BenchSuite/tree/main#licenses-"
                        )
                    })
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun ButtonItem(title: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text = title, color = Color.White)
    }
}

@Preview(showBackground = true)
@Composable
fun AboutDialogPreview() {
    AppTheme {
        AboutDialog(onDismissRequest = { })
    }
}