package com.archimatetool.asistentediagramacion;

import java.util.Map;

import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelContainer;

public final class AddInsideCapability implements Capability {
    @Override public String id() { return "add_inside_element"; }
    @Override public String description() {
        return "Crear un elemento visualmente contenido dentro de otro elemento del diagrama. Busca una posicion libre entre sus hijos, evita solapamientos y amplia el contenedor solo si hace falta. Argumentos: parent, name y element_type. No inventa una relacion semantica de composicion.";
    }

    @Override
    public Command createCommand(CapabilityContext context, Map<String, String> args) {
        CapabilitySupport.onlyKeys(args, "parent", "name", "element_type");
        IDiagramModelArchimateObject parent = context.resolveVisual(CapabilitySupport.optional(args, "parent"));
        if (!(parent instanceof IDiagramModelContainer container)) {
            throw new IllegalArgumentException("El elemento no admite contenido visual.");
        }
        String name = CapabilitySupport.requiredName(args, "name");
        IArchimateElement element = CapabilitySupport.createElement(
                CapabilitySupport.required(args, "element_type"), name);
        int width = 220;
        int height = 60;
        CapabilitySupport.InsidePlacement placement = CapabilitySupport.placeInside(parent,
                parent.getBounds().getWidth(), parent.getBounds().getHeight(), width, height, java.util.List.of());
        var visual = CapabilitySupport.visual(element, placement.x(), placement.y(), width, height);
        CompoundCommand commands = new CompoundCommand("Agregar elemento contenido");
        commands.add(CapabilitySupport.resizeContainerCommand(parent,
                placement.parentWidth(), placement.parentHeight()));
        commands.add(CapabilitySupport.addElementCommand(context, element, container, visual,
                "Agregar elemento contenido"));
        return commands;
    }

    @Override public String completionMessage(Map<String, String> args) {
        return "Elemento agregado dentro de " + args.get("parent") + ": " + args.get("name") + ".";
    }
}
