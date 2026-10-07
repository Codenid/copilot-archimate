package com.archimatetool.asistentediagramacion;

import java.util.Map;

import org.eclipse.gef.commands.Command;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFolder;

public final class EncloseElementCapability implements Capability {
    @Override public String id() { return "enclose_element"; }
    @Override public String description() {
        return "Crear un elemento ArchiMate Grouping que englobe visualmente un elemento existente. Argumentos: target y name del nuevo Grouping.";
    }

    @Override
    public Command createCommand(CapabilityContext context, Map<String, String> args) {
        CapabilitySupport.onlyKeys(args, "target", "name");
        IDiagramModelArchimateObject target = context.resolveVisual(CapabilitySupport.optional(args, "target"));
        CapabilityContext.VisualLocation location = context.locate(target);
        if (location == null) throw new IllegalArgumentException("No se encontro el elemento en el diagrama activo.");

        String name = CapabilitySupport.requiredName(args, "name");
        IArchimateElement grouping = CapabilitySupport.createElement("grouping", name);
        int oldX = target.getBounds().getX();
        int oldY = target.getBounds().getY();
        int oldWidth = target.getBounds().getWidth();
        int oldHeight = target.getBounds().getHeight();
        int x = oldX - 25;
        int y = oldY - 25;
        int width = oldWidth + 50;
        int height = oldHeight + 50;
        IDiagramModelArchimateObject groupingVisual = CapabilitySupport.visual(grouping, x, y, width, height);
        IFolder folder = context.model().getDefaultFolderForObject(grouping);
        IDiagramModelContainer oldParent = location.parent();
        int oldIndex = location.index();
        return new Command("Englobar elemento") {
            @Override public boolean canExecute() { return folder != null; }

            @Override
            public void execute() {
                boolean modelAdded = false;
                boolean groupAdded = false;
                try {
                    folder.getElements().add(grouping);
                    modelAdded = true;
                    oldParent.getChildren().remove(target);
                    target.setBounds(25, 25, oldWidth, oldHeight);
                    oldParent.getChildren().add(Math.min(oldIndex, oldParent.getChildren().size()), groupingVisual);
                    groupAdded = true;
                    groupingVisual.getChildren().add(target);
                } catch (RuntimeException exception) {
                    groupingVisual.getChildren().remove(target);
                    if (groupAdded) oldParent.getChildren().remove(groupingVisual);
                    if (!oldParent.getChildren().contains(target)) {
                        target.setBounds(oldX, oldY, oldWidth, oldHeight);
                        oldParent.getChildren().add(Math.min(oldIndex, oldParent.getChildren().size()), target);
                    }
                    if (modelAdded) folder.getElements().remove(grouping);
                    throw exception;
                }
            }

            @Override
            public void undo() {
                groupingVisual.getChildren().remove(target);
                oldParent.getChildren().remove(groupingVisual);
                target.setBounds(oldX, oldY, oldWidth, oldHeight);
                oldParent.getChildren().add(Math.min(oldIndex, oldParent.getChildren().size()), target);
                folder.getElements().remove(grouping);
            }
        };
    }

    @Override public String completionMessage(Map<String, String> args) {
        return "El elemento quedo englobado por " + args.get("name") + ".";
    }
}
