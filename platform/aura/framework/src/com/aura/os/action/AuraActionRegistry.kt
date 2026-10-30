package com.aura.os.action

class AuraActionRegistry {
    private val handlers = mutableMapOf<String, AuraActionHandler>()
    private val descriptors = mutableMapOf<String, AuraActionDescriptor>()

    @Synchronized
    fun register(descriptor: AuraActionDescriptor, handler: AuraActionHandler) {
        require(descriptor.id == handler.actionId) { "Handler ID does not match descriptor" }
        check(!handlers.containsKey(descriptor.id)) { "Duplicate action: ${descriptor.id}" }
        descriptors[descriptor.id] = descriptor
        handlers[descriptor.id] = handler
    }

    @Synchronized
    fun descriptorFor(actionId: String): AuraActionDescriptor? = descriptors[actionId]

    @Synchronized
    fun handlerFor(actionId: String): AuraActionHandler? = handlers[actionId]

    @Synchronized
    fun supportedActions(): Set<String> = descriptors.keys.toSet()
}
