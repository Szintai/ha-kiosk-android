package hu.szintai.hakiosk.ha

import hu.szintai.hakiosk.config.AppConfig
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Sends recognised text to Home Assistant's conversation agent (`/api/conversation/process`). */
@Singleton
class HaConversationClient @Inject constructor(private val api: HaApi) {

    /** @return the agent's spoken reply, e.g. "Nappali lámpa bekapcsolva". */
    suspend fun process(config: AppConfig, text: String): String {
        val body = JSONObject()
            .put("text", text)
            .put("language", config.voiceLanguage.substringBefore('-'))
        val response = api.postJson("${config.haUrl.trimEnd('/')}/api/conversation/process", config.haToken, body)
        check(response.ok) { "HA ${response.code}" }
        return JSONObject(response.body)
            .optJSONObject("response")
            ?.optJSONObject("speech")
            ?.optJSONObject("plain")
            ?.optString("speech")
            .orEmpty()
    }
}
