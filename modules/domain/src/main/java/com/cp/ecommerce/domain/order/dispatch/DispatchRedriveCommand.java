package com.cp.ecommerce.domain.order.dispatch;

public record DispatchRedriveCommand(String commandId, String dispatchId, String actor, String reason) {

    public DispatchRedriveCommand {
        if (commandId == null || commandId.isBlank() || commandId.length() > 80 || dispatchId == null || dispatchId.isBlank()
                || dispatchId.length() > 100 || actor == null || actor.isBlank() || actor.length() > 120 || reason == null
                || reason.trim().isEmpty() || reason.trim().length() > 500) {
            throw new IllegalArgumentException("Invalid dispatch redrive command identity or reason");
        }
        reason = reason.trim();
    }
}
