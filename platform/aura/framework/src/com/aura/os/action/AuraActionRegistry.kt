package com.aura.os.action

class AuraActionRegistry {
    private val handlers = mutableMapOf<String, AuraActionHandler>()

    @Synchronized
    fun register(handler: AuraActionHandler) {
        require(handler.actionId.isNotBlank()) { "actionId must not be blank" }
        check(!handlers.containsKey(handler.actionId)) { "Duplicate action: ${handler.actionId}" }
        handlers[handler.actionId] = handler
    }

    fun handlerFor(actionId: String): AuraActionHandler? = handlers[actionId]

    fun supportedActions(): Set<String> = handlers.keys.toSet()
}
