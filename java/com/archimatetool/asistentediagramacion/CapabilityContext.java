package com.archimatetool.asistentediagramacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.gef.EditPart;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.diagram.IArchimateDiagramEditor;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;

public final class CapabilityContext {
    public record VisualLocation(IDiagramModelContainer parent, IDiagramModelArchimateObject visual, int index) {}

    private final IArchimateDiagramEditor editor;
    private final IArchimateDiagramModel diagram;
    private final IArchimateModel model;
    private final List<IDiagramModelArchimateObject> selected;

    public CapabilityContext(IArchimateDiagramEditor editor, IArchimateDiagramModel diagram, IArchimateModel model) {
        this.editor = editor;
        this.diagram = diagram;
        this.model = model;
        this.selected = new ArrayList<>();
        for (Object part : editor.getGraphicalViewer().getSelectedEditParts()) {
            if (part instanceof EditPart editPart
                    && editPart.getModel() instanceof IDiagramModelArchimateObject visual) {
                selected.add(visual);
            }
        }
    }

    public IArchimateDiagramEditor editor() { return editor; }
    public IArchimateDiagramModel diagram() { return diagram; }
    public IArchimateModel model() { return model; }
    public Display display() { return editor.getSite().getShell().getDisplay(); }

    public IDiagramModelArchimateObject resolveVisual(String target) {
        if (target == null || target.isBlank()) {
            if (selected.size() == 1) return selected.get(0);
            if (selected.isEmpty()) throw new IllegalArgumentException("Selecciona un elemento o indica su nombre.");
            throw new IllegalArgumentException("Hay varios elementos seleccionados; indica cual quieres modificar.");
        }

        String expected = normalize(target);
        List<IDiagramModelArchimateObject> matches = new ArrayList<>();
        collectVisuals(diagram, expected, matches);
        if (matches.isEmpty()) throw new IllegalArgumentException("No se encontro '" + target + "' en el diagrama activo.");
        if (matches.size() > 1) throw new IllegalArgumentException("Hay varios elementos llamados '" + target + "'; especifica otro dato.");
        return matches.get(0);
    }

    public String summary() {
        List<String> names = new ArrayList<>();
        collectNames(diagram, names);
        StringBuilder result = new StringBuilder("Elementos visibles: ");
        result.append(names.isEmpty() ? "(ninguno)" : String.join(", ", names));
        result.append("\nSeleccion: ");
        if (selected.isEmpty()) {
            result.append("(ninguna)");
        } else {
            for (int i = 0; i < selected.size(); i++) {
                if (i > 0) result.append(", ");
                result.append(selected.get(i).getArchimateElement().getName());
            }
        }
        return result.toString();
    }

    public VisualLocation locate(IDiagramModelArchimateObject target) {
        return locateIn(diagram, target);
    }

    private VisualLocation locateIn(IDiagramModelContainer parent, IDiagramModelArchimateObject target) {
        for (int i = 0; i < parent.getChildren().size(); i++) {
            IDiagramModelObject child = parent.getChildren().get(i);
            if (child == target) return new VisualLocation(parent, target, i);
            if (child instanceof IDiagramModelContainer container) {
                VisualLocation found = locateIn(container, target);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void collectVisuals(IDiagramModelContainer container, String expected,
            List<IDiagramModelArchimateObject> result) {
        for (IDiagramModelObject child : container.getChildren()) {
            if (child instanceof IDiagramModelArchimateObject visual) {
                String name = visual.getArchimateElement().getName();
                if (normalize(name).equals(expected)) result.add(visual);
                collectVisuals(visual, expected, result);
            }
        }
    }

    private void collectNames(IDiagramModelContainer container, List<String> result) {
        if (result.size() >= 100) return;
        for (IDiagramModelObject child : container.getChildren()) {
            if (child instanceof IDiagramModelArchimateObject visual) {
                result.add(visual.getArchimateElement().getName() + " [" +
                        visual.getArchimateElement().eClass().getName() + "]");
                if (result.size() >= 100) return;
                collectNames(visual, result);
            }
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
