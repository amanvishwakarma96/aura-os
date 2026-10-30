package com.aura.os.action

import com.aura.os.model.AuraRiskLevel

/** Trusted, code-owned action metadata; never construct from model output. */
data class AuraActionDescriptor(
    val id: String,
    val description: String,
    val riskLevel: AuraRiskLevel,
    val requiredCapabilities: Set<String> = emptySet(),
    val parameters: Map<String, AuraParameterType> = emptyMap(),
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_.-]{2,127}")))
        require(description.isNotBlank())
        require(requiredCapabilities.none { it.isBlank() })
        require(parameters.keys.none { it.isBlank() })
    }

    fun validates(arguments: Map<String, Any?>): Boolean {
        if (arguments.keys != parameters.keys) return false
        return parameters.all { (key, type) ->
            val value = arguments[key]
            when (type) {
                AuraParameterType.STRING -> value is String && value.length <= 4096
                AuraParameterType.BOOLEAN -> value is Boolean
                AuraParameterType.INTEGER -> value is Int
            }
        }
    }
}

enum class AuraParameterType { STRING, BOOLEAN, INTEGER }
