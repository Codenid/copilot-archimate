package com.archimatetool.asistentediagramacion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.gef.commands.Command;

import com.archimatetool.editor.diagram.ArchimateDiagramModelFactory;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.util.ArchimateModelUtils;

public final class ComposeElementsCapability implements Capability {
    private static final int MAX_ELEMENTS = 20;
    private static final int MAX_GROUPS = 10;
    private static final int MAX_CONNECTIONS = 30;

    @Override public String id() { return "compose_elements"; }

    @Override
    public String description() {
        return "Construir una porcion completa del diagrama en una sola operacion deshacible. Argumento unico spec: JSON {\"elements\":[{\"name\":\"...\",\"type\":\"ApplicationComponent\",\"inside\":\"nombre opcional\",\"width\":300,\"height\":100}],\"groups\":[{\"name\":\"...\",\"members\":[\"nombre existente o nuevo\",...]}],\"connections\":[{\"source\":\"...\",\"target\":\"...\",\"type\":\"flow\"}],\"resizes\":[{\"target\":\"...\",\"width\":300,\"height\":100}]} . Los arreglos son opcionales; crear todos los nodos primero, luego agrupar, conectar y redimensionar. Al anidar, ubicar cada elemento en un espacio libre y ampliar el contenedor solo lo necesario, sin mover elementos existentes. Los nombres de referencia deben ser exactos. Solo crear elementos solicitados expresamente.";
    }

    @Override
    public Command createCommand(CapabilityContext context, Map<String, String> arguments) {
        CapabilitySupport.onlyKeys(arguments, "spec");
        String specification = CapabilitySupport.required(arguments, "spec");
        if (specification.length() > 4000) {
            throw new IllegalArgumentException("La especificacion supera 4000 caracteres.");
        }

        Builder builder = new Builder(context);
        builder.read(asObject(Json.parse(specification), "spec"));
        builder.validateAndPrepare();
        return builder.command();
    }

    @Override
    public String completionMessage(Map<String, String> arguments) {
        return "Elementos, agrupaciones, conexiones y tamaños solicitados actualizados.";
    }

    private static final class Builder {
        private final CapabilityContext context;
        private final Map<String, Node> nodes = new LinkedHashMap<>();
        private final List<Group> groups = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();
        private final List<Resize> resizes = new ArrayList<>();
        private final List<Node> roots = new ArrayList<>();
        private final Map<IDiagramModelContainer, List<CapabilitySupport.LayoutBox>> plannedChildren =
                new IdentityHashMap<>();
        private final Map<IDiagramModelContainer, int[]> plannedDimensions = new IdentityHashMap<>();
        private final List<ContainerResize> containerResizes = new ArrayList<>();
        private int nextY = 80;

        Builder(CapabilityContext context) {
            this.context = context;
            for (IDiagramModelObject child : context.diagram().getChildren()) {
                nextY = Math.max(nextY, child.getBounds().getY() + child.getBounds().getHeight() + 30);
            }
        }

        void read(Map<String, Object> spec) {
            onlyKeys(spec, Set.of("elements", "groups", "connections", "resizes"), "spec");
            List<Object> elements = optionalArray(spec, "elements");
            List<Object> rawGroups = optionalArray(spec, "groups");
            List<Object> rawEdges = optionalArray(spec, "connections");
            List<Object> rawResizes = optionalArray(spec, "resizes");
            if (elements.isEmpty() && rawGroups.isEmpty() && rawEdges.isEmpty() && rawResizes.isEmpty()) {
                throw new IllegalArgumentException("La especificacion no contiene acciones.");
            }
            if (elements.size() > MAX_ELEMENTS || rawGroups.size() > MAX_GROUPS
                    || rawEdges.size() > MAX_CONNECTIONS || rawResizes.size() > MAX_ELEMENTS) {
                throw new IllegalArgumentException("La especificacion excede los limites de elementos, agrupaciones o conexiones.");
            }

            for (Object value : elements) {
                Map<String, Object> item = asObject(value, "elements");
                onlyKeys(item, Set.of("name", "type", "inside", "x", "y", "width", "height"), "element");
                String name = validatedName(item, "name");
                String type = requiredString(item, "type");
                Node node = new Node(name, CapabilitySupport.createElement(type, name));
                node.x = optionalInteger(item, "x", 80);
                node.y = optionalInteger(item, "y", nextY);
                node.width = optionalInteger(item, "width", 300);
                node.height = optionalInteger(item, "height", 70);
                validateBounds(node.width, node.height);
                node.inside = optionalString(item, "inside");
                addNode(node);
                nextY = Math.max(nextY, node.y + node.height + 30);
            }

            for (Object value : rawGroups) {
                Map<String, Object> item = asObject(value, "groups");
                onlyKeys(item, Set.of("name", "members"), "group");
                String name = validatedName(item, "name");
                List<String> members = stringArray(item.get("members"), "members");
                if (members.isEmpty()) throw new IllegalArgumentException("El Grouping '" + name + "' requiere miembros.");
                Node node = new Node(name, CapabilitySupport.createElement("grouping", name));
                node.group = true;
                addNode(node);
                groups.add(new Group(node, members));
            }

            for (Object value : rawEdges) {
                Map<String, Object> item = asObject(value, "connections");
                onlyKeys(item, Set.of("source", "target", "type"), "connection");
                edges.add(new Edge(requiredString(item, "source"), requiredString(item, "target"),
                        requiredString(item, "type")));
            }

            for (Object value : rawResizes) {
                Map<String, Object> item = asObject(value, "resizes");
                onlyKeys(item, Set.of("target", "width", "height", "factor"), "resize");
                resizes.add(new Resize(requiredString(item, "target"), optionalInteger(item, "width", -1),
                        optionalInteger(item, "height", -1), optionalNumber(item, "factor")));
            }
        }

        void validateAndPrepare() {
            Set<String> diagramNames = new HashSet<>();
            collectNames(context.diagram(), diagramNames);
            for (Node node : nodes.values()) {
                if (diagramNames.contains(normalize(node.name))) {
                    throw new IllegalArgumentException("Ya hay un elemento llamado '" + node.name + "' en el diagrama.");
                }
                for (var iterator = context.model().eAllContents(); iterator.hasNext();) {
                    Object current = iterator.next();
                    if (current instanceof IArchimateElement existing
                            && existing.eClass() == node.element.eClass()
                            && existing.getName().equalsIgnoreCase(node.name)) {
                        throw new IllegalArgumentException("Ya existe un elemento de ese tipo llamado '" + node.name + "'.");
                    }
                }
            }

            for (Node node : nodes.values()) {
                if (!node.group && !node.inside.isBlank()) {
                    ParentRef parent = resolveParent(node.inside);
                    if (!parent.container()) {
                        throw new IllegalArgumentException("'" + node.inside + "' no admite elementos contenidos.");
                    }
                    node.parent = parent;
                } else {
                    node.parent = ParentRef.root(context.diagram());
                }
            }

            for (Group group : groups) {
                ParentRef commonParent = null;
                Set<String> groupedNames = new HashSet<>();
                for (String memberName : group.members) {
                    if (!groupedNames.add(normalize(memberName))) {
                        throw new IllegalArgumentException("El miembro '" + memberName + "' esta repetido en el Grouping.");
                    }
                    Node member = nodes.get(normalize(memberName));
                    ParentRef memberParent;
                    if (member != null) {
                        memberParent = member.parent;
                    } else {
                        IDiagramModelArchimateObject visual = context.resolveVisual(memberName);
                        CapabilityContext.VisualLocation location = context.locate(visual);
                        if (location == null) throw new IllegalArgumentException("No se encontro '" + memberName + "'.");
                        memberParent = ParentRef.existing(location.parent());
                        group.existingMembers.add(new ExistingMember(visual, location));
                    }
                    if (commonParent == null) commonParent = memberParent;
                    else if (!commonParent.same(memberParent)) {
                        throw new IllegalArgumentException("Los miembros del Grouping '" + group.node.name
                                + "' deben ser hermanos dentro del mismo contenedor.");
                    }
                    if (member != null) {
                        if (member.group) throw new IllegalArgumentException("No se admite anidar Groupings dentro de otros Groupings.");
                        if (member.groupedBy != null) throw new IllegalArgumentException("El elemento '"
                                + member.name + "' aparece en mas de un Grouping.");
                        member.groupedBy = group;
                    }
                }
                group.node.parent = commonParent;
            }

            Set<String> allGroupedNames = new HashSet<>();
            for (Group group : groups) {
                for (String memberName : group.members) {
                    if (!allGroupedNames.add(normalize(memberName))) {
                        throw new IllegalArgumentException("Un elemento no puede pertenecer a mas de un Grouping en la misma instruccion.");
                    }
                }
            }

            for (Node node : nodes.values()) {
                if (node.groupedBy != null) node.parent = ParentRef.generated(node.groupedBy.node);
            }
            validateParentGraph();

            Set<String> resizedNames = new HashSet<>();
            for (Resize resize : resizes) {
                if (!resizedNames.add(normalize(resize.target))) {
                    throw new IllegalArgumentException("El elemento '" + resize.target + "' aparece mas de una vez en resizes.");
                }
                Node node = nodes.get(normalize(resize.target));
                if (node != null) {
                    int[] dimensions = dimensions(node.width, node.height,
                            resize.requestedWidth, resize.requestedHeight, resize.factor);
                    node.width = dimensions[0];
                    node.height = dimensions[1];
                } else {
                    IDiagramModelArchimateObject visual = context.resolveVisual(resize.target);
                    resize.existing = visual;
                    resize.oldX = visual.getBounds().getX();
                    resize.oldY = visual.getBounds().getY();
                    resize.oldWidth = visual.getBounds().getWidth();
                    resize.oldHeight = visual.getBounds().getHeight();
                    int[] dimensions = dimensions(resize.oldWidth, resize.oldHeight,
                            resize.requestedWidth, resize.requestedHeight, resize.factor);
                    resize.width = dimensions[0];
                    resize.height = dimensions[1];
                }
            }

            for (Node node : nodes.values()) {
                node.visual = CapabilitySupport.visual(node.element, node.x, node.y, node.width, node.height);
            }
            layoutNestedNodes();
            for (Group group : groups) prepareGroup(group);
            for (Node node : nodes.values()) {
                if (node.parent.generated != null
                        && !(node.parent.generated.visual instanceof IDiagramModelContainer)) {
                    throw new IllegalArgumentException("'" + node.parent.generated.name
                            + "' no admite elementos contenidos.");
                }
                if (node.parent.generated == null) roots.add(node);
                else node.parent.generated.visual.getChildren().add(node.visual);
            }
            prepareEdges();
        }

        private void layoutNestedNodes() {
            List<Node> nested = nodes.values().stream()
                    .filter(node -> !node.group && node.groupedBy == null && !node.inside.isBlank())
                    .sorted((left, right) -> Integer.compare(parentDepth(right), parentDepth(left)))
                    .toList();
            for (Node node : nested) {
                IDiagramModelContainer parent = node.parent.generated == null
                        ? node.parent.existing : node.parent.generated.visual;
                int[] currentDimensions = plannedDimensions.get(parent);
                if (currentDimensions == null) {
                    currentDimensions = new int[] { node.parent.generated == null
                            ? ((IDiagramModelArchimateObject) parent).getBounds().getWidth()
                            : node.parent.generated.width,
                            node.parent.generated == null
                            ? ((IDiagramModelArchimateObject) parent).getBounds().getHeight()
                            : node.parent.generated.height };
                }
                List<CapabilitySupport.LayoutBox> siblings =
                        plannedChildren.computeIfAbsent(parent, ignored -> new ArrayList<>());
                CapabilitySupport.InsidePlacement placement = CapabilitySupport.placeInside(parent,
                        currentDimensions[0], currentDimensions[1], node.width, node.height, siblings);
                node.x = placement.x();
                node.y = placement.y();
                node.visual.setBounds(node.x, node.y, node.width, node.height);
                siblings.add(new CapabilitySupport.LayoutBox(node.x, node.y, node.width, node.height));
                plannedDimensions.put(parent, new int[] { placement.parentWidth(), placement.parentHeight() });

                if (node.parent.generated != null) {
                    node.parent.generated.width = placement.parentWidth();
                    node.parent.generated.height = placement.parentHeight();
                    node.parent.generated.visual.setBounds(node.parent.generated.x, node.parent.generated.y,
                            node.parent.generated.width, node.parent.generated.height);
                } else {
                    recordContainerResize((IDiagramModelArchimateObject) parent,
                            placement.parentWidth(), placement.parentHeight());
                }
            }
        }

        private int parentDepth(Node node) {
            int depth = 0;
            Node parent = node.parent.generated;
            while (parent != null) {
                depth++;
                parent = parent.parent == null ? null : parent.parent.generated;
            }
            return depth;
        }

        private void recordContainerResize(IDiagramModelArchimateObject parent, int width, int height) {
            for (ContainerResize resize : containerResizes) {
                if (resize.parent == parent) {
                    resize.width = Math.max(resize.width, width);
                    resize.height = Math.max(resize.height, height);
                    return;
                }
            }
            containerResizes.add(new ContainerResize(parent, width, height));
        }

        private void prepareGroup(Group group) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            int insertionIndex = Integer.MAX_VALUE;
            for (String memberName : group.members) {
                Node member = nodes.get(normalize(memberName));
                int x, y, width, height;
                if (member != null) {
                    x = member.x; y = member.y; width = member.width; height = member.height;
                } else {
                    ExistingMember existing = findExisting(group, memberName);
                    var bounds = existing.visual.getBounds();
                    x = bounds.getX(); y = bounds.getY();
                    int[] resized = resizedDimensions(existing.visual, bounds.getWidth(), bounds.getHeight());
                    width = resized[0]; height = resized[1];
                    insertionIndex = Math.min(insertionIndex, existing.location.index());
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + width);
                maxY = Math.max(maxY, y + height);
            }
            group.node.x = minX - 25;
            group.node.y = minY - 25;
            group.node.width = maxX - minX + 50;
            group.node.height = maxY - minY + 50;
            if (group.node.width < 60 || group.node.height < 40) {
                throw new IllegalArgumentException("No se pudo calcular el tamaño del Grouping '" + group.node.name + "'.");
            }
            group.node.visual = CapabilitySupport.visual(group.node.element, group.node.x, group.node.y,
                    group.node.width, group.node.height);
            group.insertionIndex = insertionIndex == Integer.MAX_VALUE ? -1 : insertionIndex;
            for (String memberName : group.members) {
                Node member = nodes.get(normalize(memberName));
                if (member != null) {
                    member.visual.setBounds(member.x - minX + 25, member.y - minY + 25,
                            member.width, member.height);
                } else {
                    ExistingMember existing = findExisting(group, memberName);
                    var bounds = existing.visual.getBounds();
                    existing.newX = bounds.getX() - minX + 25;
                    existing.newY = bounds.getY() - minY + 25;
                    int[] resized = resizedDimensions(existing.visual, bounds.getWidth(), bounds.getHeight());
                    existing.newWidth = resized[0];
                    existing.newHeight = resized[1];
                }
            }
        }

        private int[] resizedDimensions(IDiagramModelArchimateObject visual, int width, int height) {
            for (Resize resize : resizes) {
                if (resize.existing == visual) return new int[] { resize.width, resize.height };
            }
            return new int[] { width, height };
        }

        private void prepareEdges() {
            Set<String> unique = new HashSet<>();
            for (Edge edge : edges) {
                Node source = nodes.get(normalize(edge.source));
                Node target = nodes.get(normalize(edge.target));
                IArchimateConcept sourceConcept = source == null
                        ? context.resolveVisual(edge.source).getArchimateElement() : source.element;
                IArchimateConcept targetConcept = target == null
                        ? context.resolveVisual(edge.target).getArchimateElement() : target.element;
                String normalized = CapabilitySupport.normalizeType(edge.type);
                String className = Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1)
                        + "Relationship";
                EClassifier classifier = IArchimatePackage.eINSTANCE.getEClassifier(className);
                if (!(classifier instanceof EClass eClass) || eClass.isAbstract()
                        || eClass.getInstanceClass() == null
                        || !IArchimateRelationship.class.isAssignableFrom(eClass.getInstanceClass())) {
                    throw new IllegalArgumentException("Tipo de relacion no reconocido: " + edge.type);
                }
                if (sourceConcept == targetConcept) {
                    throw new IllegalArgumentException("El origen y el destino de una conexion deben ser distintos.");
                }
                if (!ArchimateModelUtils.isValidRelationship(sourceConcept.eClass(), targetConcept.eClass(), eClass)) {
                    throw new IllegalArgumentException("La relacion '" + edge.type
                            + "' no es valida entre '" + edge.source + "' y '" + edge.target + "'.");
                }
                String key = System.identityHashCode(sourceConcept) + ":" + System.identityHashCode(targetConcept)
                        + ":" + eClass.getName();
                if (!unique.add(key)) throw new IllegalArgumentException("La conexion aparece mas de una vez.");
                IArchimateRelationship relationship = findRelationship(sourceConcept, targetConcept, eClass);
                boolean addRelationship = relationship == null;
                if (addRelationship) relationship = (IArchimateRelationship) IArchimateFactory.eINSTANCE.create(eClass);
                IDiagramModelArchimateObject sourceVisual = source == null
                        ? context.resolveVisual(edge.source) : source.visual;
                IDiagramModelArchimateObject targetVisual = target == null
                        ? context.resolveVisual(edge.target) : target.visual;
                edge.connection = ArchimateDiagramModelFactory.createDiagramModelArchimateConnection(relationship);
                edge.sourceConcept = sourceConcept;
                edge.targetConcept = targetConcept;
                edge.relationship = relationship;
                edge.addRelationship = addRelationship;
                edge.relationshipFolder = addRelationship
                        ? context.model().getDefaultFolderForObject(relationship) : null;
                edge.sourceVisual = sourceVisual;
                edge.targetVisual = targetVisual;
                if (addRelationship && edge.relationshipFolder == null) {
                    throw new IllegalArgumentException("No se encontro una carpeta para guardar la relacion.");
                }
            }
        }

        private Command command() {
            return new Command("Construir diagrama") {
                private final List<Node> attachedRoots = new ArrayList<>();
                private final List<ExistingMember> movedMembers = new ArrayList<>();
                private final List<Edge> createdEdges = new ArrayList<>();
                private final List<Node> addedElements = new ArrayList<>();
                private final List<Resize> resized = new ArrayList<>();
                private final List<ContainerResize> expandedContainers = new ArrayList<>();

                @Override public boolean canExecute() { return true; }

                @Override
                public void execute() {
                    try {
                        for (Node node : nodes.values()) {
                            IFolder folder = context.model().getDefaultFolderForObject(node.element);
                            if (folder == null) throw new IllegalStateException("No se encontro carpeta para " + node.name + ".");
                            node.folder = folder;
                            addedElements.add(node);
                            folder.getElements().add(node.element);
                        }
                        for (Resize resize : resizes) {
                            if (resize.existing != null) {
                                resized.add(resize);
                                resize.existing.setBounds(resize.oldX, resize.oldY, resize.width, resize.height);
                            }
                        }
                        for (ContainerResize resize : containerResizes) {
                            expandedContainers.add(resize);
                            resize.parent.setBounds(resize.oldX, resize.oldY,
                                    Math.max(resize.oldWidth, resize.width),
                                    Math.max(resize.oldHeight, resize.height));
                        }
                        for (Node node : roots) {
                            IDiagramModelContainer parent = node.parent.existing;
                            attachedRoots.add(node);
                            parent.getChildren().add(node.visual);
                        }
                        for (Group group : groups) {
                            for (ExistingMember existing : group.existingMembers) {
                                movedMembers.add(existing);
                                existing.location.parent().getChildren().remove(existing.visual);
                                existing.visual.setBounds(existing.newX, existing.newY, existing.newWidth, existing.newHeight);
                                group.node.visual.getChildren().add(existing.visual);
                            }
                            if (group.insertionIndex >= 0) {
                                IDiagramModelContainer parent = group.node.parent.existing;
                                int index = Math.min(group.insertionIndex, parent.getChildren().size());
                                parent.getChildren().remove(group.node.visual);
                                parent.getChildren().add(index, group.node.visual);
                            }
                        }
                        for (Edge edge : edges) {
                            createdEdges.add(edge);
                            if (edge.addRelationship) {
                                edge.semanticAdded = true;
                                edge.relationshipFolder.getElements().add(edge.relationship);
                                edge.relationship.connect(edge.sourceConcept, edge.targetConcept);
                                edge.semanticConnected = true;
                            }
                            edge.visualConnected = true;
                            edge.connection.connect(edge.sourceVisual, edge.targetVisual);
                        }
                    } catch (RuntimeException exception) {
                        undoChanges();
                        throw exception;
                    }
                }

                @Override public void undo() { undoChanges(); }

                private void undoChanges() {
                    for (int i = createdEdges.size() - 1; i >= 0; i--) {
                        Edge edge = createdEdges.get(i);
                        if (edge.visualConnected) edge.connection.disconnect();
                        if (edge.semanticConnected) edge.relationship.disconnect();
                        if (edge.semanticAdded) {
                            edge.relationshipFolder.getElements().remove(edge.relationship);
                        }
                    }
                    createdEdges.clear();
                    for (int i = movedMembers.size() - 1; i >= 0; i--) {
                        ExistingMember member = movedMembers.get(i);
                        Group group = groupContaining(member);
                        group.node.visual.getChildren().remove(member.visual);
                        member.visual.setBounds(member.oldX, member.oldY, member.oldWidth, member.oldHeight);
                        if (!member.location.parent().getChildren().contains(member.visual)) {
                            member.location.parent().getChildren().add(
                            Math.min(member.location.index(), member.location.parent().getChildren().size()), member.visual);
                        }
                    }
                    movedMembers.clear();
                    for (int i = attachedRoots.size() - 1; i >= 0; i--) {
                        Node node = attachedRoots.get(i);
                        node.parent.existing.getChildren().remove(node.visual);
                    }
                    attachedRoots.clear();
                    for (int i = resized.size() - 1; i >= 0; i--) {
                        Resize resize = resized.get(i);
                        resize.existing.setBounds(resize.oldX, resize.oldY, resize.oldWidth, resize.oldHeight);
                    }
                    resized.clear();
                    for (int i = expandedContainers.size() - 1; i >= 0; i--) {
                        ContainerResize resize = expandedContainers.get(i);
                        resize.parent.setBounds(resize.oldX, resize.oldY, resize.oldWidth, resize.oldHeight);
                    }
                    expandedContainers.clear();
                    for (int i = addedElements.size() - 1; i >= 0; i--) {
                        Node node = addedElements.get(i);
                        node.folder.getElements().remove(node.element);
                    }
                    addedElements.clear();
                }
            };
        }

        private void validateParentGraph() {
            for (Node node : nodes.values()) {
                Set<Node> visited = new HashSet<>();
                Node current = node;
                while (current.parent != null && current.parent.generated != null) {
                    current = current.parent.generated;
                    if (!visited.add(current) || current == node) {
                        throw new IllegalArgumentException("La contencion de elementos contiene un ciclo.");
                    }
                }
            }
        }

        private ParentRef resolveParent(String name) {
            Node generated = nodes.get(normalize(name));
            if (generated != null) return ParentRef.generated(generated);
            IDiagramModelArchimateObject visual = context.resolveVisual(name);
            return ParentRef.existing(visual);
        }

        private void addNode(Node node) {
            String key = normalize(node.name);
            if (nodes.putIfAbsent(key, node) != null) {
                throw new IllegalArgumentException("El nombre '" + node.name + "' esta repetido en la especificacion.");
            }
        }

        private ExistingMember findExisting(Group group, String name) {
            String expected = normalize(name);
            for (ExistingMember member : group.existingMembers) {
                if (normalize(member.visual.getArchimateElement().getName()).equals(expected)) return member;
            }
            throw new IllegalArgumentException("No se encontro el miembro '" + name + "' del Grouping.");
        }

        private IArchimateRelationship findRelationship(IArchimateConcept source, IArchimateConcept target, EClass type) {
            for (IArchimateRelationship relationship : source.getSourceRelationships()) {
                if (relationship.getTarget() == target && relationship.eClass() == type) return relationship;
            }
            return null;
        }

        private Group groupContaining(ExistingMember member) {
            for (Group group : groups) if (group.existingMembers.contains(member)) return group;
            throw new IllegalStateException("Se perdio el Grouping de un elemento.");
        }
    }

    private static int[] dimensions(int oldWidth, int oldHeight, int requestedWidth,
            int requestedHeight, Double requestedFactor) {
        int width = requestedWidth < 0 ? oldWidth : requestedWidth;
        int height = requestedHeight < 0 ? oldHeight : requestedHeight;
        if (requestedFactor != null) {
            double factor = requestedFactor;
            if (factor > 4 && factor <= 300) factor = 1 + factor / 100;
            if (factor < 1.05 || factor > 4) {
                throw new IllegalArgumentException("El factor de tamaño debe estar entre 1.05 y 4 o entre 5 y 300 por ciento.");
            }
            width = (int) Math.round(oldWidth * factor);
            height = (int) Math.round(oldHeight * factor);
        } else if (requestedWidth < 0 && requestedHeight < 0) {
            width = (int) Math.round(oldWidth * 1.25);
            height = (int) Math.round(oldHeight * 1.25);
        }
        validateBounds(width, height);
        return new int[] { width, height };
    }

    private static void validateBounds(int width, int height) {
        if (width < 60 || width > 3000 || height < 40 || height > 3000) {
            throw new IllegalArgumentException("El ancho debe estar entre 60 y 3000 y la altura entre 40 y 3000.");
        }
    }

    private static Map<String, Object> asObject(Object value, String field) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("Clave invalida en " + field + ".");
                result.put(key, entry.getValue());
            }
            return result;
        }
        throw new IllegalArgumentException("'" + field + "' debe ser un objeto JSON.");
    }

    private static List<Object> optionalArray(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) return List.of();
        if (value instanceof List<?> values) return new ArrayList<>(values);
        throw new IllegalArgumentException("'" + key + "' debe ser una lista.");
    }

    private static List<String> stringArray(Object value, String field) {
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException("'" + field + "' debe ser una lista.");
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("'" + field + "' solo admite nombres de elementos.");
            }
            result.add(text.trim());
        }
        return result;
    }

    private static String requiredString(Map<String, Object> item, String key) {
        Object value = item.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Falta el texto '" + key + "'.");
        }
        return text.trim();
    }

    private static String optionalString(Map<String, Object> item, String key) {
        Object value = item.get(key);
        if (value == null) return "";
        if (!(value instanceof String text)) throw new IllegalArgumentException("'" + key + "' debe ser texto.");
        return text.trim();
    }

    private static int optionalInteger(Map<String, Object> item, String key, int defaultValue) {
        Object value = item.get(key);
        if (value == null) return defaultValue;
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.doubleValue() < Integer.MIN_VALUE || number.doubleValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("'" + key + "' debe ser un entero.");
        }
        return number.intValue();
    }

    private static String validatedName(Map<String, Object> item, String key) {
        String name = requiredString(item, key);
        if (name.length() > 160 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("'" + key + "' supera 160 caracteres o contiene controles.");
        }
        return name;
    }

    private static Double optionalNumber(Map<String, Object> item, String key) {
        Object value = item.get(key);
        if (value == null) return null;
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("'" + key + "' debe ser numerico.");
        }
        return number.doubleValue();
    }

    private static void onlyKeys(Map<String, Object> object, Set<String> allowed, String field) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("Campo '" + key + "' no admitido en " + field + ".");
        }
    }

    private static void collectNames(IDiagramModelContainer container, Set<String> names) {
        for (IDiagramModelObject child : container.getChildren()) {
            if (child instanceof IDiagramModelArchimateObject visual) {
                names.add(normalize(visual.getArchimateElement().getName()));
                collectNames(visual, names);
            }
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Node {
        final String name;
        final IArchimateElement element;
        boolean group;
        String inside = "";
        int x, y, width, height;
        ParentRef parent;
        Group groupedBy;
        IDiagramModelArchimateObject visual;
        IFolder folder;

        Node(String name, IArchimateElement element) {
            this.name = name;
            this.element = element;
        }
    }

    private static final class ParentRef {
        final IDiagramModelContainer existing;
        final Node generated;
        private ParentRef(IDiagramModelContainer existing, Node generated) {
            this.existing = existing;
            this.generated = generated;
        }
        static ParentRef root(IDiagramModelContainer parent) { return new ParentRef(parent, null); }
        static ParentRef existing(IDiagramModelContainer parent) { return new ParentRef(parent, null); }
        static ParentRef generated(Node parent) { return new ParentRef(null, parent); }
        boolean container() { return generated == null || generated.visual == null
                || generated.visual instanceof IDiagramModelContainer; }
        boolean same(ParentRef other) {
            return existing != null ? existing == other.existing : generated == other.generated;
        }
    }

    private static final class Group {
        final Node node;
        final List<String> members;
        final List<ExistingMember> existingMembers = new ArrayList<>();
        int insertionIndex = -1;
        Group(Node node, List<String> members) { this.node = node; this.members = members; }
    }

    private static final class ExistingMember {
        final IDiagramModelArchimateObject visual;
        final CapabilityContext.VisualLocation location;
        final int oldX, oldY, oldWidth, oldHeight;
        int newX, newY, newWidth, newHeight;
        ExistingMember(IDiagramModelArchimateObject visual, CapabilityContext.VisualLocation location) {
            this.visual = visual;
            this.location = location;
            this.oldX = visual.getBounds().getX();
            this.oldY = visual.getBounds().getY();
            this.oldWidth = visual.getBounds().getWidth();
            this.oldHeight = visual.getBounds().getHeight();
        }
    }

    private static final class Edge {
        final String source, target, type;
        IArchimateConcept sourceConcept, targetConcept;
        IArchimateRelationship relationship;
        IFolder relationshipFolder;
        IDiagramModelArchimateObject sourceVisual, targetVisual;
        IDiagramModelArchimateConnection connection;
        boolean addRelationship;
        boolean semanticAdded, semanticConnected, visualConnected;
        Edge(String source, String target, String type) {
            this.source = source; this.target = target; this.type = type;
        }
    }

    private static final class Resize {
        final String target;
        final int requestedWidth, requestedHeight;
        final Double factor;
        IDiagramModelArchimateObject existing;
        int oldX, oldY, oldWidth, oldHeight;
        int width, height;
        Resize(String target, int width, int height, Double factor) {
            this.target = target; this.requestedWidth = width; this.requestedHeight = height; this.factor = factor;
        }
    }

    private static final class ContainerResize {
        final IDiagramModelArchimateObject parent;
        final int oldX, oldY, oldWidth, oldHeight;
        int width, height;
        ContainerResize(IDiagramModelArchimateObject parent, int width, int height) {
            this.parent = parent;
            this.oldX = parent.getBounds().getX();
            this.oldY = parent.getBounds().getY();
            this.oldWidth = parent.getBounds().getWidth();
            this.oldHeight = parent.getBounds().getHeight();
            this.width = width;
            this.height = height;
        }
    }
}
