package com.qawse.openpage.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qawse.openpage.R
import com.qawse.openpage.ui.components.InfoBanner
import com.qawse.openpage.ui.components.OpenCard
import com.qawse.openpage.ui.components.SectionHeader
import com.qawse.openpage.ui.theme.LocalOpenColors
import com.qawse.openpage.ui.theme.Tokens
import com.qawse.openpage.viewmodel.AppViewModel

private const val PAYPAL_DONATE = "https://www.paypal.com/qrcodes/p2pqrc/375DMEAZC8JU2"

@Composable
fun AboutScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalOpenColors.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "2.0.0"
    }
    // Release builds unlock diagnostics by tapping the version five times.
    var taps by rememberSaveable { mutableStateOf(0) }
    val diagnosticsUnlocked by vm.settingsStore.diagnosticsUnlocked.collectAsState()
    val width = rememberWidth()

    ScreenScaffold(title = stringResource(R.string.about_title), width = width, onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = Tokens.SpaceXXL),
        ) {
            // Brand block -------------------------------------------------------
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.SpaceLG),
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(Tokens.RadiusMD))
                            .background(colors.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(Tokens.SpaceLG))
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Tokens.SpaceXS + 2.dp))
                    Text(
                        stringResource(R.string.app_publisher),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.app_founder),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Tokens.SpaceXXL),
                    )
                    Spacer(Modifier.height(Tokens.SpaceXS))
                    Text(
                        stringResource(R.string.app_version, version),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .tapVersion {
                                taps++
                                if (taps >= 5 && !diagnosticsUnlocked) {
                                    vm.settingsStore.setDiagnosticsUnlocked(true)
                                }
                            }
                            .padding(Tokens.SpaceXS),
                    )
                }
            }

            // Support ------------------------------------------------------------
            item {
                OpenCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.about_support),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Tokens.SpaceXS + 2.dp))
                        Text(
                            stringResource(R.string.about_support_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(Tokens.SpaceSM))
                // Donation CTA — the one place the brand accent speaks.
                Surface(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(PAYPAL_DONATE))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    shape = MaterialTheme.shapes.small,
                    color = colors.accent,
                    contentColor = colors.onAccent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Tokens.SpaceLG + Tokens.SpaceXS)
                        ,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = Tokens.SpaceLG),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_heart),
                            contentDescription = null,
                            modifier = Modifier.size(Tokens.IconMD),
                        )
                        Spacer(Modifier.width(Tokens.SpaceSM))
                        Text(
                            stringResource(R.string.about_donate),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onAccent,
                        )
                    }
                }
                Text(
                    stringResource(R.string.about_donate_opens),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Tokens.SpaceXS),
                )
            }

            // App description ------------------------------------------------------
            item { SectionHeader(stringResource(R.string.legal_app_description_title)) }
            item { LegalCard(stringResource(R.string.legal_app_description)) }

            // Company ---------------------------------------------------------------
            item { SectionHeader(stringResource(R.string.about_company_title)) }
            item { LegalCard(stringResource(R.string.about_company_blurb)) }

            // Services -----------------------------------------------------------------
            item { SectionHeader(stringResource(R.string.legal_services_title)) }
            item { LegalCard(stringResource(R.string.legal_services)) }

            // Terms -----------------------------------------------------------------------
            item { SectionHeader(stringResource(R.string.legal_terms_title)) }
            item { ExpandableLegalCard(stringResource(R.string.legal_terms)) }

            // Legal ---------------------------------------------------------------------------
            item { SectionHeader(stringResource(R.string.legal_legal_title)) }
            item { ExpandableLegalCard(stringResource(R.string.legal_legal)) }

            // Licenses ---------------------------------------------------------------------------
            item { SectionHeader(stringResource(R.string.legal_licenses_title)) }
            item { ExpandableLegalCard(stringResource(R.string.legal_licenses)) }

            // Credits ---------------------------------------------------------------------------
            item { SectionHeader(stringResource(R.string.legal_credits_title)) }
            item { LegalCard(stringResource(R.string.legal_credits)) }

            item {
                Spacer(Modifier.height(Tokens.SpaceSM))
                Text(
                    stringResource(R.string.app_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.SpaceMD),
                )
                InfoBanner(
                    text = stringResource(R.string.about_thank_you),
                    icon = R.drawable.ic_heart,
                    tone = com.qawse.openpage.ui.components.BannerTone.INFO,
                )
            }
        }
    }
}

private fun Modifier.tapVersion(onTap: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onTap, enabled = true))

@Composable
private fun LegalCard(text: String) {
    val colors = LocalOpenColors.current
    OpenCard {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpandableLegalCard(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colors = LocalOpenColors.current
    OpenCard(onClick = { expanded = !expanded }, modifier = Modifier.animateContentSize()) {
        Column {
            Text(
                if (expanded) text else text.take(200) + "…",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(Tokens.SpaceSM))
            Text(
                if (expanded) stringResource(R.string.action_less)
                else stringResource(R.string.action_more),
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
            )
        }
    }
}
