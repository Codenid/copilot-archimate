package com.archimatetool.asistentediagramacion;

import java.util.Map;

import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IDiagramModelObject;

public final class CreateElementCapability implements Capability {
    @Override public String id() { return "create_element"; }
    @Override public String description() {
        return "Crear cualquier tipo concreto de elemento ArchiMate disponible en el catalogo dinamico del metamodelo de Archi. No asumir un tipo por defecto. Argumentos: name y element_type (usar el identificador exacto del catalogo).";
    }

    @Override
    public Command createCommand(CapabilityContext context, Map<String, String> args) {
        CapabilitySupport.onlyKeys(args, "name", "element_type");
        String name = CapabilitySupport.requiredName(args, "name");
        String type = CapabilitySupport.required(args, "element_type");
        IArchimateElement element = CapabilitySupport.createElement(type, name);
        for (var iterator = context.model().eAllContents(); iterator.hasNext();) {
            Object current = iterator.next();
            if (current instanceof IArchimateElement existing
                    && existing.eClass() == element.eClass()
                    && existing.getName().equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("Ya existe un elemento de ese tipo llamado '" + name + "'.");
            }
        }

        int y = 80;
        for (IDiagramModelObject child : context.diagram().getChildren()) {
            y = Math.max(y, child.getBounds().getY() + child.getBounds().getHeight() + 30);
        }
        CompoundCommand commands = new CompoundCommand("Crear elemento");
        commands.add(CapabilitySupport.addElementCommand(context, element, context.diagram(),
                CapabilitySupport.visual(element, 80, y, 300, 70), "Crear elemento"));
        return commands;
    }

    @Override public String completionMessage(Map<String, String> args) {
        return "Elemento creado: " + args.get("name") + " (" + args.get("element_type") + ").";
    }
}
