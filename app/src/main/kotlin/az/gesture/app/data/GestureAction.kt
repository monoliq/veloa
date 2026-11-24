package az.gesture.app.data

import org.json.JSONObject

data class GestureAction(
    val type: ActionType,
    val param: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type.name)
        if (!param.isNullOrBlank()) {
            put("param", param)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject?): GestureAction {
            if (obj == null) return GestureAction(ActionType.NONE)
            val type = ActionType.safeValueOf(obj.optString("type"))
            val param = obj.optString("param").takeIf { it.isNotBlank() }
            return GestureAction(type, param)
        }
    }
}
