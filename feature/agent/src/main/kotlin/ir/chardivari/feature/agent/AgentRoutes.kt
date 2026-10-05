package ir.chardivari.feature.agent

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Internal agent shell — five stable tabs under a single AGENT route.
 * Root customer tabs stay untouched (Profile entry → AGENT).
 */
internal object AgentRoutes {
    const val SHELL = "agent"
    const val DASHBOARD = "agent/dashboard"
    const val FILES = "agent/files"
    const val LEADS = "agent/leads"
    const val VISITS = "agent/visits"
    const val PROFILE = "agent/profile"
}

internal enum class AgentTab(
    val route: String,
    val labelFa: String,
    val icon: ImageVector,
) {
    DASHBOARD(AgentRoutes.DASHBOARD, "داشبورد", Icons.Outlined.Dashboard),
    FILES(AgentRoutes.FILES, "فایل‌ها", Icons.Outlined.FolderOpen),
    LEADS(AgentRoutes.LEADS, "سرنخ‌ها", Icons.Outlined.People),
    VISITS(AgentRoutes.VISITS, "بازدیدها", Icons.Outlined.Event),
    PROFILE(AgentRoutes.PROFILE, "حساب", Icons.Outlined.Person),
    ;

    companion object {
        val all: List<AgentTab> = entries.toList()
    }
}
