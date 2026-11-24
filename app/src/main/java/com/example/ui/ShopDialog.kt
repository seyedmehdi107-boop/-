package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.game.CreatureSkin
import com.example.game.GameUiState
import com.example.game.UpgradeType
import com.example.ui.theme.CrimsonInk
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.InkMist
import com.example.ui.theme.InkPale
import com.example.ui.theme.InkSketch
import com.example.ui.theme.ParchmentPaper
import com.example.ui.theme.ParchmentSurface
import com.example.ui.theme.ParchmentWarm

private val GoldCoinColor = Color(0xFFC59432)

@Composable
fun ShopDialog(
    state: GameUiState,
    onSkinAction: (CreatureSkin) -> Unit,
    onUpgradeAction: (UpgradeType) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            InkFrameCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .heightIn(max = 660.dp)
                    .testTag("shop_dialog"),
                backgroundColor = ParchmentSurface,
            ) {
                // Header with Coin Balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.shop_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = InkBlack,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoinBalanceChip(coins = state.totalCoins)
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("close_shop_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close_label),
                                tint = InkBlack,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tabs: Skins vs Level Up
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShopTabButton(
                        title = stringResource(R.string.tab_skins),
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        testTag = "tab_skins",
                        modifier = Modifier.weight(1f),
                    )
                    ShopTabButton(
                        title = stringResource(R.string.tab_upgrades),
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        testTag = "tab_upgrades",
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                InkDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == 0) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        items(CreatureSkin.entries, key = { it.id }) { skin ->
                            val isUnlocked = skin in state.unlockedSkins
                            val isEquipped = skin == state.equippedSkin
                            val canAfford = state.totalCoins >= skin.priceCoins
                            SkinItemCard(
                                skin = skin,
                                isUnlocked = isUnlocked,
                                isEquipped = isEquipped,
                                canAfford = canAfford,
                                onAction = { onSkinAction(skin) },
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        items(UpgradeType.entries, key = { it.name }) { upgrade ->
                            val level = when (upgrade) {
                                UpgradeType.MAGNET -> state.magnetLevel
                                UpgradeType.BOUNTY -> state.bountyLevel
                                UpgradeType.AGILITY -> state.agilityLevel
                            }
                            UpgradeItemCard(
                                upgrade = upgrade,
                                currentLevel = level,
                                totalCoins = state.totalCoins,
                                onUpgrade = { onUpgradeAction(upgrade) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoinBalanceChip(
    coins: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(ParchmentWarm, RoundedCornerShape(6.dp))
            .border(1.4.dp, InkBlack, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.MonetizationOn,
            contentDescription = null,
            tint = GoldCoinColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = coins.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = InkBlack,
        )
    }
}

@Composable
private fun ShopTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) InkBlack else ParchmentWarm)
            .border(1.5.dp, InkBlack, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) ParchmentSurface else InkBlack,
        )
    }
}

@Composable
private fun SkinItemCard(
    skin: CreatureSkin,
    isUnlocked: Boolean,
    isEquipped: Boolean,
    canAfford: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isEquipped) ParchmentWarm else ParchmentPaper.copy(alpha = 0.65f),
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (isEquipped) 2.2.dp else 1.2.dp,
                color = if (isEquipped) CrimsonInk else InkSketch,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Miniature artwork preview showing the creature on the man's shoulder
        Image(
            painter = painterResource(id = skin.menuDrawableRes),
            contentDescription = stringResource(skin.nameRes),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.4.dp, InkBlack, RoundedCornerShape(6.dp)),
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(skin.nameRes),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = InkBlack,
                )
                Spacer(modifier = Modifier.width(6.dp))
                // HP hearts display
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(skin.maxHp) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = CrimsonInk,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(skin.descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = InkMist,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        val btnEnabled = isUnlocked && !isEquipped || (!isUnlocked && canAfford)
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        isEquipped -> CrimsonInk
                        isUnlocked -> InkBlack
                        canAfford -> InkCharcoal
                        else -> InkPale.copy(alpha = 0.6f)
                    }
                )
                .clickable(enabled = btnEnabled, onClick = onAction)
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .testTag("skin_btn_${skin.id}"),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isEquipped -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = ParchmentSurface, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.equipped_label), style = MaterialTheme.typography.labelMedium, color = ParchmentSurface)
                }
                isUnlocked -> Text(
                    text = stringResource(R.string.equip_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = ParchmentSurface,
                )
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (canAfford) Icons.Default.MonetizationOn else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (canAfford) GoldCoinColor else InkSketch,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = skin.priceCoins.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canAfford) ParchmentSurface else InkSketch,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpgradeItemCard(
    upgrade: UpgradeType,
    currentLevel: Int,
    totalCoins: Int,
    onUpgrade: () -> Unit,
) {
    val nextCost = upgrade.nextCost(currentLevel)
    val canAfford = nextCost != null && totalCoins >= nextCost
    val icon = when (upgrade) {
        UpgradeType.MAGNET -> Icons.Default.Bolt
        UpgradeType.BOUNTY -> Icons.Default.Stars
        UpgradeType.AGILITY -> Icons.Default.Upgrade
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ParchmentPaper.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .border(1.3.dp, InkSketch, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(ParchmentWarm, CircleShape)
                .border(1.4.dp, InkBlack, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = InkBlack, modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(upgrade.nameRes),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = InkBlack,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.level_label, currentLevel, 5),
                    style = MaterialTheme.typography.labelMedium,
                    color = CrimsonInk,
                )
            }
            Text(
                text = stringResource(upgrade.descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = InkMist,
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Level progress pips
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(5) { idx ->
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (idx < currentLevel) InkBlack else InkPale.copy(alpha = 0.55f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        nextCost == null -> InkPale.copy(alpha = 0.65f)
                        canAfford -> InkBlack
                        else -> InkPale.copy(alpha = 0.6f)
                    }
                )
                .clickable(enabled = canAfford, onClick = onUpgrade)
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .testTag("upgrade_btn_${upgrade.name.lowercase()}"),
            contentAlignment = Alignment.Center,
        ) {
            if (nextCost == null) {
                Text(stringResource(R.string.max_level_label), style = MaterialTheme.typography.labelMedium, color = InkSketch)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = null,
                        tint = if (canAfford) GoldCoinColor else InkSketch,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = nextCost.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canAfford) ParchmentSurface else InkSketch,
                    )
                }
            }
        }
    }
}
