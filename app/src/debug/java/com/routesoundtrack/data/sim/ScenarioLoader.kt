package com.routesoundtrack.data.sim

import android.content.Context
import org.json.JSONObject

/** assets 폴더의 시나리오 JSON 파일을 읽는다. 디버그 빌드 전용. */
object ScenarioLoader {

    fun load(context: Context, path: String): Scenario =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }

    fun parse(json: String): Scenario {
        val o = JSONObject(json)
        val dest = o.getJSONObject("destination")
        val kfArray = o.getJSONArray("keyframes")
        val keyframes = List(kfArray.length()) { i ->
            val k = kfArray.getJSONObject(i)
            Keyframe(k.getDouble("km"), k.getDouble("kmh"), k.optDouble("brakePerMin", 0.0))
        }
        val clock = o.optJSONObject("clock")
        return Scenario(
            name = o.optString("name", "scenario"),
            destinationName = dest.optString("name", ""),
            totalKm = dest.getDouble("totalKm"),
            keyframes = keyframes.sortedBy { it.km },
            silentRate = clock?.optDouble("silentRate", 20.0) ?: 20.0,
            playingRate = clock?.optDouble("playingRate", 1.0) ?: 1.0,
        )
    }
}
