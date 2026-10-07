package com.archimatetool.asistentediagramacion;

import java.util.Map;
import java.util.Objects;

import org.eclipse.gef.commands.Command;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.IProperties;

public final class SetPropertyCapability implements Capability {
    @Override public String id() { return "set_property"; }
    @Override public String description() {
        return "Agregar o actualizar una propiedad de un elemento. Argumentos: target, property_key y property_value. No modifica atributos internos de ArchiMate.";
    }

    @Override
    public Command createCommand(CapabilityContext context, Map<String, String> args) {
        CapabilitySupport.onlyKeys(args, "target", "property_key", "property_value");
        String key = CapabilitySupport.required(args, "property_key");
        if (key.length() > 255 || key.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("La clave supera 255 caracteres o contiene controles.");
        }
        if (!args.containsKey("property_value")) {
            throw new IllegalArgumentException("Falta el dato 'property_value'.");
        }
        String value = args.get("property_value");
        if (value.length() > 4000) throw new IllegalArgumentException("El valor supera 4000 caracteres.");
        IArchimateConcept concept = context.resolveVisual(CapabilitySupport.optional(args, "target"))
                .getArchimateElement();
        IProperties properties = concept;
        IProperty existing = null;
        for (IProperty property : properties.getProperties()) {
            if (property.getKey().equalsIgnoreCase(key)) {
                if (existing != null) throw new IllegalArgumentException("Hay propiedades duplicadas con esa clave.");
                existing = property;
            }
        }
        if (existing == null) {
            IProperty created = IArchimateFactory.eINSTANCE.createProperty(key, value);
            return new Command("Agregar propiedad") {
                @Override public void execute() { properties.getProperties().add(created); }
                @Override public void undo() { properties.getProperties().remove(created); }
            };
        }

        IProperty property = existing;
        String previous = property.getValue();
        if (Objects.equals(previous, value)) throw new IllegalArgumentException("La propiedad ya tiene ese valor.");
        return CapabilitySupport.change("Actualizar propiedad", () -> property.setValue(value),
                () -> property.setValue(previous));
    }

    @Override public String completionMessage(Map<String, String> args) {
        return "Propiedad actualizada: " + args.get("property_key") + ".";
    }
}
