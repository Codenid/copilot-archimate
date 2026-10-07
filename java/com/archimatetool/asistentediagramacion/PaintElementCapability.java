package com.archimatetool.asistentediagramacion;

import java.util.Map;

import org.eclipse.gef.commands.Command;

import com.archimatetool.model.IDiagramModelArchimateObject;

public final class PaintElementCapability implements Capability {
    @Override public String id() { return "paint_element"; }
    @Override public String description() {
        return "Cambiar el color de un elemento. Argumentos: target, color (nombre de color en espanol o #RRGGBB) y area (relleno, borde o texto; default relleno).";
    }

    @Override
    public Command createCommand(CapabilityContext context, Map<String, String> args) {
        CapabilitySupport.onlyKeys(args, "target", "color", "area");
        IDiagramModelArchimateObject visual = context.resolveVisual(CapabilitySupport.optional(args, "target"));
        String color = CapabilitySupport.color(CapabilitySupport.required(args, "color"));
        String area = CapabilitySupport.optional(args, "area");
        if (area.isEmpty()) area = "relleno";
        return switch (area.toLowerCase()) {
            case "relleno", "fill" -> {
                String previous = visual.getFillColor();
                yield CapabilitySupport.change("Pintar elemento", () -> visual.setFillColor(color),
                        () -> visual.setFillColor(previous));
            }
            case "borde", "linea", "line", "border" -> {
                String previous = visual.getLineColor();
                yield CapabilitySupport.change("Cambiar color de borde", () -> visual.setLineColor(color),
                        () -> visual.setLineColor(previous));
            }
            case "texto", "font", "text" -> {
                String previous = visual.getFontColor();
                yield CapabilitySupport.change("Cambiar color de texto", () -> visual.setFontColor(color),
                        () -> visual.setFontColor(previous));
            }
            default -> throw new IllegalArgumentException("El area debe ser relleno, borde o texto.");
        };
    }

    @Override public String completionMessage(Map<String, String> args) { return "Color del elemento actualizado."; }
}
