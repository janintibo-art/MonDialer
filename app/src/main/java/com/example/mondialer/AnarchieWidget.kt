package com.example.mondialer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.RemoteViews

/** Widget d'accueil : compteur de blocages et accès direct aux favoris. */
class AnarchieWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, manager, id)
    }

    companion object {
        /** À appeler dès qu'un blocage est enregistré, pour rafraîchir le compteur. */
        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, AnarchieWidget::class.java))
                for (id in ids) render(context, manager, id)
            } catch (_: Exception) {}
        }

        private fun favorites(context: Context): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) return out
            try {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER),
                    ContactsContract.CommonDataKinds.Phone.STARRED + "=1",
                    null, null
                )?.use { c ->
                    while (c.moveToNext() && out.size < 3) {
                        val name = c.getString(0) ?: continue
                        if (out.none { it.first == name })
                            out.add(Pair(name, c.getString(1) ?: ""))
                    }
                }
            } catch (_: Exception) {}
            return out
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            BlockRulesStore.appCtx = context.applicationContext
            val views = RemoteViews(context.packageName, R.layout.widget_anarchie)

            // Compteur : ouvre le mur de la honte
            val count = try { BlockRulesStore.blockedLog().size } catch (e: Exception) { 0 }
            views.setTextViewText(R.id.wCount, count.toString())
            views.setOnClickPendingIntent(R.id.wCountBox,
                activity(context, Intent(context, StatsActivity::class.java), 200))

            // Logo : ouvre le clavier
            views.setOnClickPendingIntent(R.id.wLogo,
                activity(context, Intent(context, MainActivity::class.java), 201))

            // Trois favoris : préremplissent le numéro dans le clavier
            val favs = favorites(context)
            val slots = intArrayOf(R.id.wFav1, R.id.wFav2, R.id.wFav3)
            for (i in slots.indices) {
                val f = favs.getOrNull(i)
                if (f == null) {
                    views.setTextViewText(slots[i], "—")
                    views.setOnClickPendingIntent(slots[i],
                        activity(context, Intent(context, ContactsActivity::class.java), 210 + i))
                } else {
                    views.setTextViewText(slots[i], "★ " + f.first.split(" ").first())
                    val call = Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData(Uri.parse("tel:" + f.second.filter {
                            ch -> ch.isDigit() || ch == '+' }))
                    views.setOnClickPendingIntent(slots[i], activity(context, call, 220 + i))
                }
            }

            // Messages
            views.setOnClickPendingIntent(R.id.wSms,
                activity(context, Intent(context, ConversationsActivity::class.java), 230))

            manager.updateAppWidget(id, views)
        }

        private fun activity(context: Context, intent: Intent, code: Int): PendingIntent {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
