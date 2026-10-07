package com.archimatetool.asistentediagramacion;

import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.gef.commands.Command;

import com.archimatetool.editor.diagram.ArchimateDiagramModelFactory;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFolder;

final class CapabilitySupport {
    record LayoutBox(int x, int y, int width, int height) {}
    record InsidePlacement(int x, int y, int parentWidth, int parentHeight) {}

    private CapabilitySupport() {}

    static String required(Map<String, String> args, String key) {
        String value = args.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta el dato '" + key + "'.");
        }
        return value.trim();
    }

    static String requiredName(Map<String, String> args, String key) {
        String value = required(args, key);
        if (value.length() > 160 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("'" + key + "' supera 160 caracteres o contiene controles.");
        }
        return value;
    }

    static String optional(Map<String, String> args, String key) {
        return args.getOrDefault(key, "").trim();
    }

    static void onlyKeys(Map<String, String> args, String... allowed) {
        java.util.Set<String> keys = java.util.Set.of(allowed);
        for (String key : args.keySet()) {
            if (!keys.contains(key)) throw new IllegalArgumentException("Argumento no admitido: " + key);
        }
    }

    static int integer(Map<String, String> args, String key) {
        try {
            return Integer.parseInt(required(args, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("'" + key + "' debe ser un numero entero.");
        }
    }

    static IArchimateElement createElement(String typeId, String name) {
        String normalized = typeId.trim().replaceAll("[^A-Za-z0-9]", "");
        if (normalized.isEmpty()) throw new IllegalArgumentException("Indica el tipo ArchiMate del elemento.");
        EClassifier classifier = null;
        for (EClassifier candidate : IArchimatePackage.eINSTANCE.getEClassifiers()) {
            if (candidate instanceof EClass eClass && isConcreteElementType(eClass)
                    && candidate.getName().replaceAll("[^A-Za-z0-9]", "").equalsIgnoreCase(normalized)) {
                classifier = candidate;
                break;
            }
        }
        if (!(classifier instanceof EClass eClass)) {
            throw new IllegalArgumentException("Tipo ArchiMate no reconocido: " + typeId);
        }
        Object created = IArchimateFactory.eINSTANCE.create(eClass);
        if (!(created instanceof IArchimateElement element)) {
            throw new IllegalArgumentException("El tipo no es un elemento ArchiMate: " + typeId);
        }
        element.setName(name);
        return element;
    }

    static List<String> elementTypeIds() {
        List<String> types = new ArrayList<>();
        for (EClassifier candidate : IArchimatePackage.eINSTANCE.getEClassifiers()) {
            if (candidate instanceof EClass eClass && isConcreteElementType(eClass)) {
                types.add(eClass.getName());
            }
        }
        types.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(types);
    }

    private static boolean isConcreteElementType(EClass eClass) {
        Class<?> instanceClass = eClass.getInstanceClass();
        return !eClass.isAbstract() && instanceClass != null
                && IArchimateElement.class.isAssignableFrom(instanceClass);
    }

    static IDiagramModelArchimateObject visual(IArchimateElement element, int x, int y, int width, int height) {
        IDiagramModelArchimateObject visual =
                ArchimateDiagramModelFactory.createDiagramModelArchimateObject(element);
        visual.setBounds(x, y, width, height);
        return visual;
    }

    static InsidePlacement placeInside(IDiagramModelContainer parent, int parentWidth, int parentHeight,
            int childWidth, int childHeight, List<LayoutBox> plannedChildren) {
        List<LayoutBox> obstacles = new ArrayList<>(plannedChildren);
        for (IDiagramModelObject child : parent.getChildren()) {
            var bounds = child.getBounds();
            obstacles.add(new LayoutBox(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()));
        }

        int gap = 20;
        TreeSet<Integer> xCandidates = new TreeSet<>();
        TreeSet<Integer> yCandidates = new TreeSet<>();
        xCandidates.add(30);
        yCandidates.add(30);
        for (LayoutBox obstacle : obstacles) {
            xCandidates.add(Math.max(30, obstacle.x() + obstacle.width() + gap));
            yCandidates.add(Math.max(30, obstacle.y() + obstacle.height() + gap));
            int left = obstacle.x() - childWidth - gap;
            int above = obstacle.y() - childHeight - gap;
            if (left >= 30) xCandidates.add(left);
            if (above >= 30) yCandidates.add(above);
        }

        InsidePlacement best = null;
        long bestGrowth = Long.MAX_VALUE;
        long bestDistance = Long.MAX_VALUE;
        for (int y : yCandidates) {
            for (int x : xCandidates) {
                boolean overlaps = false;
                for (LayoutBox obstacle : obstacles) {
                    if (x < obstacle.x() + obstacle.width() + gap
                            && x + childWidth + gap > obstacle.x()
                            && y < obstacle.y() + obstacle.height() + gap
                            && y + childHeight + gap > obstacle.y()) {
                        overlaps = true;
                        break;
                    }
                }
                if (overlaps) continue;

                int requiredWidth = Math.max(parentWidth, x + childWidth + 30);
                int requiredHeight = Math.max(parentHeight, y + childHeight + 30);
                long growth = (long) requiredWidth * requiredHeight - (long) parentWidth * parentHeight;
                long distance = (long) Math.abs(x - 30) + Math.abs(y - 30);
                if (growth < bestGrowth || (growth == bestGrowth && distance < bestDistance)
                        || (growth == bestGrowth && distance == bestDistance
                                && (best == null || y < best.y() || (y == best.y() && x < best.x())))) {
                    best = new InsidePlacement(x, y, requiredWidth, requiredHeight);
                    bestGrowth = growth;
                    bestDistance = distance;
                }
            }
        }
        if (best == null) throw new IllegalStateException("No se encontro una ubicacion para el elemento.");
        return best;
    }

    static Command resizeContainerCommand(IDiagramModelArchimateObject container, int width, int height) {
        int x = container.getBounds().getX();
        int y = container.getBounds().getY();
        int oldWidth = container.getBounds().getWidth();
        int oldHeight = container.getBounds().getHeight();
        if (width <= oldWidth && height <= oldHeight) {
            return change("Mantener tamano del contenedor", () -> {}, () -> {});
        }
        return change("Ampliar contenedor para mostrar el elemento",
                () -> container.setBounds(x, y, Math.max(width, oldWidth), Math.max(height, oldHeight)),
                () -> container.setBounds(x, y, oldWidth, oldHeight));
    }

    static Command addElementCommand(CapabilityContext context, IArchimateElement element,
            IDiagramModelContainer parent, IDiagramModelArchimateObject visual, String label) {
        IFolder folder = context.model().getDefaultFolderForObject(element);
        return new Command(label) {
            @Override
            public boolean canExecute() {
                return folder != null && parent != null && element != null && visual != null;
            }

            @Override
            public void execute() {
                boolean added = false;
                try {
                    folder.getElements().add(element);
                    added = true;
                    parent.getChildren().add(visual);
                } catch (RuntimeException exception) {
                    if (parent.getChildren().contains(visual)) parent.getChildren().remove(visual);
                    if (added && folder.getElements().contains(element)) folder.getElements().remove(element);
                    throw exception;
                }
            }

            @Override
            public void undo() {
                parent.getChildren().remove(visual);
                folder.getElements().remove(element);
            }
        };
    }

    static Command change(String label, Runnable execute, Runnable undo) {
        return new Command(label) {
            @Override public void execute() { execute.run(); }
            @Override public void undo() { undo.run(); }
        };
    }

    static String normalizeType(String value) {
        return value.trim().replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    static String color(String input) {
        String value = input.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "rojo", "red" -> "#ff0000";
            case "verde", "green" -> "#00aa00";
            case "azul", "blue" -> "#0000ff";
            case "amarillo", "yellow" -> "#ffff00";
            case "naranja", "orange" -> "#ff9900";
            case "morado", "purple", "violeta" -> "#800080";
            case "negro", "black" -> "#000000";
            case "blanco", "white" -> "#ffffff";
            case "gris", "gray", "grey" -> "#808080";
            default -> {
                if (!value.matches("#[0-9a-f]{6}")) {
                    throw new IllegalArgumentException("Usa un color admitido o un hexadecimal #RRGGBB.");
                }
                yield value;
            }
        };
    }
}
