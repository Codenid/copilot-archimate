package com.archimatetool.asistentediagramacion;

import java.util.Map;

import org.eclipse.gef.commands.Command;

import com.archimatetool.editor.diagram.ArchimateDiagramModelFactory;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.util.ArchimateModelUtils;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;

public final class ConnectElementsCapability implements Capability {
    @Override public String id() { return "connect_elements"; }
    @Override public String description() {
        return "Conectar (agregar un conector entre) dos elementos visibles con una relacion ArchiMate. Argumentos: source opcional (usa la seleccion unica), target y relationship_type (association, composition, aggregation, flow, triggering, access, serving, realization, assignment, influence, specialization).";
    }

    @Override
    public Command createCommand(CapabilityContext context, Map<String, String> args) {
        CapabilitySupport.onlyKeys(args, "source", "target", "relationship_type");
        IDiagramModelArchimateObject source = context.resolveVisual(CapabilitySupport.optional(args, "source"));
        IDiagramModelArchimateObject target = context.resolveVisual(CapabilitySupport.required(args, "target"));
        if (source == target) throw new IllegalArgumentException("El origen y el destino deben ser distintos.");

        String relationshipType = CapabilitySupport.required(args, "relationship_type");
        String normalized = CapabilitySupport.normalizeType(relationshipType);
        if (!SetOfRelationshipTypes.VALUES.contains(normalized)) {
            throw new IllegalArgumentException("Tipo de relacion no admitido: " + relationshipType);
        }
        String className = Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1) + "Relationship";
        EClassifier classifier = IArchimatePackage.eINSTANCE.getEClassifier(className);
        if (!(classifier instanceof EClass eClass)) {
            throw new IllegalArgumentException("No se reconocio la relacion ArchiMate: " + relationshipType);
        }
        Object created = IArchimateFactory.eINSTANCE.create(eClass);
        if (!(created instanceof IArchimateRelationship newRelationship)) {
            throw new IllegalArgumentException("No se reconocio la relacion ArchiMate: " + relationshipType);
        }
        IArchimateConcept sourceConcept = source.getArchimateElement();
        IArchimateConcept targetConcept = target.getArchimateElement();
        if (!ArchimateModelUtils.isValidRelationship(
                sourceConcept.eClass(), targetConcept.eClass(), eClass)) {
            throw new IllegalArgumentException("Esa relacion no es valida entre los tipos de origen y destino.");
        }
        IArchimateRelationship relationship = null;
        for (IArchimateRelationship existing : sourceConcept.getSourceRelationships()) {
            if (existing.getTarget() == targetConcept && existing.eClass() == eClass) {
                relationship = existing;
                break;
            }
        }
        boolean addSemanticRelationship = relationship == null;
        IArchimateRelationship selectedRelationship = addSemanticRelationship ? newRelationship : relationship;

        IDiagramModelArchimateConnection connection =
                ArchimateDiagramModelFactory.createDiagramModelArchimateConnection(selectedRelationship);
        IFolder folder = addSemanticRelationship
                ? context.model().getDefaultFolderForObject(selectedRelationship) : null;
        return new Command("Conectar elementos") {
            @Override
            public boolean canExecute() {
                return context.diagram() != null && (!addSemanticRelationship || folder != null);
            }

            @Override
            public void execute() {
                boolean added = false, modelConnected = false, viewConnected = false;
                try {
                    if (addSemanticRelationship) {
                        folder.getElements().add(selectedRelationship);
                        added = true;
                        selectedRelationship.connect(sourceConcept, targetConcept);
                        modelConnected = true;
                    }
                    connection.connect(source, target);
                    viewConnected = true;
                } catch (RuntimeException exception) {
                    if (viewConnected) connection.disconnect();
                    if (modelConnected) selectedRelationship.disconnect();
                    if (added && folder.getElements().contains(selectedRelationship)) {
                        folder.getElements().remove(selectedRelationship);
                    }
                    throw exception;
                }
            }

            @Override
            public void undo() {
                connection.disconnect();
                if (addSemanticRelationship) {
                    selectedRelationship.disconnect();
                    folder.getElements().remove(selectedRelationship);
                }
            }
        };
    }

    @Override public String completionMessage(Map<String, String> args) {
        return "Elementos conectados con " + args.get("relationship_type") + ".";
    }

    private static final class SetOfRelationshipTypes {
        static final java.util.Set<String> VALUES = java.util.Set.of(
            "association", "composition", "aggregation", "flow", "triggering",
            "access", "serving", "realization", "assignment", "influence", "specialization"
        );
    }
}
