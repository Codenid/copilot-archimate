package com.archimatetool.asistentediagramacion;

import java.util.Map;

import org.eclipse.gef.commands.Command;

import com.archimatetool.model.IDiagramModelArchimateObject;

public final class ResizeElementCapability implements Capability {
    @Override public String id() { return "resize_element"; }
    @Override public String description() {
        return "Ampliar un elemento (por defecto 25 por ciento) o cambiar su tamano. Argumentos: target y factor, o width y height en pixeles.";
    }

    @Override
    public Command createCommand(CapabilityContext context, Map<String, String> args) {
        CapabilitySupport.onlyKeys(args, "target", "width", "height", "factor");
        IDiagramModelArchimateObject visual = context.resolveVisual(CapabilitySupport.optional(args, "target"));
        int x = visual.getBounds().getX();
        int y = visual.getBounds().getY();
        int oldWidth = visual.getBounds().getWidth();
        int oldHeight = visual.getBounds().getHeight();
        String widthText = CapabilitySupport.optional(args, "width");
        String heightText = CapabilitySupport.optional(args, "height");
        String factorText = CapabilitySupport.optional(args, "factor");
        int width;
        int height;
        if (!factorText.isEmpty()) {
            double factor;
            try { factor = Double.parseDouble(factorText); }
            catch (NumberFormatException exception) {
                throw new IllegalArgumentException("'factor' debe ser numerico.");
            }
            if (factor > 4 && factor <= 300) factor = 1 + factor / 100;
            if (factor < 1.05 || factor > 4) {
                throw new IllegalArgumentException("El factor debe ser un multiplicador entre 1.05 y 4 o un porcentaje entre 5 y 300.");
            }
            width = (int)Math.round(oldWidth * factor);
            height = (int)Math.round(oldHeight * factor);
        } else if (widthText.isEmpty() && heightText.isEmpty()) {
            width = (int)Math.round(oldWidth * 1.25);
            height = (int)Math.round(oldHeight * 1.25);
        } else {
            width = widthText.isEmpty() ? oldWidth : parseDimension(widthText, "width");
            height = heightText.isEmpty() ? oldHeight : parseDimension(heightText, "height");
        }
        if (width < 60 || width > 3000 || height < 40 || height > 3000) {
            throw new IllegalArgumentException("El ancho debe estar entre 60 y 3000 y la altura entre 40 y 3000.");
        }
        return CapabilitySupport.change("Cambiar tamano", () -> visual.setBounds(x, y, width, height),
                () -> visual.setBounds(x, y, oldWidth, oldHeight));
    }

    private int parseDimension(String value, String key) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException("'" + key + "' debe ser un numero entero.");
        }
    }

    @Override public String completionMessage(Map<String, String> args) { return "Tamano del elemento actualizado."; }
}
