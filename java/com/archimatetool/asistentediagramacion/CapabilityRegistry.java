package com.archimatetool.asistentediagramacion;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;

public final class CapabilityRegistry {
    public static final String EXTENSION_POINT = "com.archimatetool.asistentediagramacion.capabilities";
    private final Map<String, Capability> capabilities = new LinkedHashMap<>();

    public CapabilityRegistry() throws CoreException {
        for (IConfigurationElement element :
                Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT)) {
            if (!"capability".equals(element.getName())) continue;
            Object executable = element.createExecutableExtension("class");
            if (!(executable instanceof Capability capability)) {
                throw new CoreException(new org.eclipse.core.runtime.Status(
                    org.eclipse.core.runtime.IStatus.ERROR,
                    "com.archimatetool.asistentediagramacion",
                    "La extension no implementa Capability: " + element.getAttribute("class")));
            }
            if (capability.id() == null || capability.id().isBlank()
                    || capabilities.putIfAbsent(capability.id(), capability) != null) {
                throw new CoreException(new org.eclipse.core.runtime.Status(
                    org.eclipse.core.runtime.IStatus.ERROR,
                    "com.archimatetool.asistentediagramacion",
                    "Identificador de capacidad invalido o duplicado: " + capability.id()));
            }
        }
        if (capabilities.isEmpty()) {
            throw new CoreException(new org.eclipse.core.runtime.Status(
                org.eclipse.core.runtime.IStatus.ERROR,
                "com.archimatetool.asistentediagramacion",
                "No hay capacidades de asistente registradas."));
        }
    }

    public Capability get(String id) {
        return capabilities.get(id);
    }

    public Iterable<Capability> all() {
        return capabilities.values();
    }
}
