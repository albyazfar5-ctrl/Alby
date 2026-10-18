package com.example.simplelauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FolderData(val id: String, var name: String, val pkgs: MutableList<String>)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    var columns: Int
        get() = sp.getInt("columns", 4)
        set(v) = sp.edit().putInt("columns", v).apply()

    var rows: Int
        get() = sp.getInt("rows", 5)
        set(v) = sp.edit().putInt("rows", v).apply()

    var iconStyle: Int
        get() = sp.getInt("iconStyle", 0)
        set(v) = sp.edit().putInt("iconStyle", v).apply()

    var badgesEnabled: Boolean
        get() = sp.getBoolean("badges", false)
        set(v) = sp.edit().putBoolean("badges", v).apply()

    var hidden: MutableSet<String>
        get() = HashSet(sp.getStringSet("hidden", emptySet()) ?: emptySet())
        set(v) = sp.edit().putStringSet("hidden", HashSet(v)).apply()

    var folders: MutableList<FolderData>
        get() {
            val list = mutableListOf<FolderData>()
            try {
                val arr = JSONArray(sp.getString("folders", "[]"))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val p = o.getJSONArray("pkgs")
                    val pk = mutableListOf<String>()
                    for (j in 0 until p.length()) pk.add(p.getString(j))
                    list.add(FolderData(o.getString("id"), o.getString("name"), pk))
                }
            } catch (e: Exception) {
            }
            return list
        }
        set(v) {
            val arr = JSONArray()
            v.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("pkgs", JSONArray(it.pkgs))
                )
            }
            sp.edit().putString("folders", arr.toString()).apply()
        }
}
