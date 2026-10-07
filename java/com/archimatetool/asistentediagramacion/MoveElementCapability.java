package com.archimatetool.asistentediagramacion;

import java.util.Map;

import org.eclipse.gef.commands.Command;
import com.archimatetool.model.IDiagramModelArchimateObject;

public final class MoveElementCapability implements Capability {
    @Override public String id() { return "move_element"; }
    @Override public String description() {
        return "Mover un elemento del diagrama. Argumentos: target y (x e y para posicion absoluta), dx y dy, o direction (arriba, abajo, izquierda, derecha) con distance opcional.";
    }

    @Override
    public Command createCommand(CapabilityContext context, Map<String, String> args) {
        CapabilitySupport.onlyKeys(args, "target", "x", "y", "dx", "dy", "direction", "distance");
        IDiagramModelArchimateObject visual = context.resolveVisual(CapabilitySupport.optional(args, "target"));
        int oldX = visual.getBounds().getX();
        int oldY = visual.getBounds().getY();
        int width = visual.getBounds().getWidth();
        int height = visual.getBounds().getHeight();
        String xText = CapabilitySupport.optional(args, "x");
        String yText = CapabilitySupport.optional(args, "y");
        int x, y;
        if (!xText.isEmpty() || !yText.isEmpty()) {
            if (xText.isEmpty() || yText.isEmpty()) {
                throw new IllegalArgumentException("Para mover a una posicion absoluta indica x e y.");
            }
            x = parseCoordinate(xText, "x");
            y = parseCoordinate(yText, "y");
        } else {
            String dxText = CapabilitySupport.optional(args, "dx");
            String dyText = CapabilitySupport.optional(args, "dy");
            if (dxText.isEmpty() || dyText.isEmpty()) {
                String direction = CapabilitySupport.required(args, "direction").toLowerCase();
                int distance = 100;
                String distanceText = CapabilitySupport.optional(args, "distance");
                if (!distanceText.isEmpty()) {
                    try { distance = Integer.parseInt(distanceText); }
                    catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("'distance' debe ser un numero entero.");
                    }
                }
                if (distance < 1 || distance > 2000) {
                    throw new IllegalArgumentException("La distancia debe estar entre 1 y 2000 pixeles.");
                }
                switch (direction) {
                    case "arriba", "up" -> { dxText = "0"; dyText = String.valueOf(-distance); }
                    case "abajo", "down" -> { dxText = "0"; dyText = String.valueOf(distance); }
                    case "izquierda", "left" -> { dxText = String.valueOf(-distance); dyText = "0"; }
                    case "derecha", "right" -> { dxText = String.valueOf(distance); dyText = "0"; }
                    default -> throw new IllegalArgumentException("Indica arriba, abajo, izquierda o derecha.");
                }
            }
            int dx;
            int dy;
            try {
                dx = Integer.parseInt(dxText);
                dy = Integer.parseInt(dyText);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("'dx' y 'dy' deben ser numeros enteros.");
            }
            x = oldX + dx;
            y = oldY + dy;
        }
        checkBounds(x, y);
        return CapabilitySupport.change("Mover elemento", () -> visual.setBounds(x, y, width, height),
                () -> visual.setBounds(oldX, oldY, width, height));
    }

    private int parseCoordinate(String value, String axis) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException("'" + axis + "' debe ser un numero entero.");
        }
    }

    private void checkBounds(int x, int y) {
        if (Math.abs((long) x) > 10000 || Math.abs((long) y) > 10000) {
            throw new IllegalArgumentException("La posicion solicitada esta fuera del limite permitido.");
        }
    }

    @Override public String completionMessage(Map<String, String> args) { return "Elemento movido."; }
}
