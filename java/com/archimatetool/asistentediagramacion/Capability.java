package com.archimatetool.asistentediagramacion;

import java.util.Map;

import org.eclipse.gef.commands.Command;

public interface Capability {
    String id();
    String description();
    Command createCommand(CapabilityContext context, Map<String, String> arguments) throws Exception;
    String completionMessage(Map<String, String> arguments);
}
