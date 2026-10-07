package com.archimatetool.asistentediagramacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import com.archimatetool.editor.diagram.IArchimateDiagramEditor;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

public final class AssistantView extends ViewPart {
    public static final String ID = "com.archimatetool.asistentediagramacion.view";
    private static final int MAX_HISTORY_MESSAGES = 12;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final List<Map<String, String>> history = new ArrayList<>();
    private Text transcript;
    private Text instruction;
    private Button submit;
    private Label status;
    private CapabilityRegistry registry;

    @Override
    public void createPartControl(Composite parent) {
        Composite root = new Composite(parent, SWT.NONE);
        root.setLayout(new GridLayout(1, false));

        transcript = new Text(root, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        transcript.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        transcript.setText("Asistente de diagramacion. Selecciona un diagrama ArchiMate e ingresa una instruccion.");

        instruction = new Text(root, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
        instruction.setMessage("Escribe tu instruccion...");
        instruction.setTextLimit(4000);
        GridData instructionData = new GridData(SWT.FILL, SWT.FILL, true, false);
        instructionData.heightHint = 65;
        instruction.setLayoutData(instructionData);
        instruction.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if ((event.keyCode == SWT.CR || event.keyCode == SWT.KEYPAD_CR)
                        && (event.stateMask & SWT.ALT) == 0) {
                    event.doit = false;
                    submitInstruction();
                }
            }
        });

        submit = new Button(root, SWT.PUSH);
        submit.setText("Enviar");
        submit.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        submit.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                submitInstruction();
            }
        });

        status = new Label(root, SWT.WRAP);
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        try {
            registry = new CapabilityRegistry();
            setStatus("Capacidades activas: " + countCapabilities());
        } catch (CoreException exception) {
            submit.setEnabled(false);
            setStatus("No se pudieron cargar las capacidades: " + exception.getMessage());
        }
    }

    private int countCapabilities() {
        int count = 0;
        for (Capability ignored : registry.all()) count++;
        return count;
    }

    private void submitInstruction() {
        if (registry == null || !busy.compareAndSet(false, true)) return;
        String text = instruction.getText().trim();
        if (text.isEmpty()) {
            busy.set(false);
            setStatus("Escribe tu instruccion antes de enviar.");
            instruction.setFocus();
            return;
        }
        if (text.length() > 4000) {
            busy.set(false);
            setStatus("La instruccion supera 4000 caracteres.");
            return;
        }

        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        IEditorPart activeEditor = page.getActiveEditor();
        if (!(activeEditor instanceof IArchimateDiagramEditor diagramEditor)
                || !(diagramEditor.getModel() instanceof IArchimateDiagramModel diagram)) {
            busy.set(false);
            setStatus("Abre y selecciona una vista ArchiMate antes de enviar.");
            return;
        }
        EObject root = EcoreUtil.getRootContainer(diagram);
        if (!(root instanceof IArchimateModel model)) {
            busy.set(false);
            setStatus("No se pudo identificar el modelo de la vista activa.");
            return;
        }

        CapabilityContext context = new CapabilityContext(diagramEditor, diagram, model);
        String contextSummary = context.summary();
        List<Capability> capabilities = new ArrayList<>();
        for (Capability capability : registry.all()) capabilities.add(capability);
        List<Map<String, String>> previousMessages = List.copyOf(history);
        history.add(Map.of("role", "user", "content", text));
        appendTranscript("Tu: " + text);
        instruction.setText("");
        submit.setEnabled(false);
        setStatus("construyendo...");

        Display display = instruction.getDisplay();
        Thread worker = new Thread(() -> {
            OllamaClient.Plan plan = null;
            String error = null;
            try {
                plan = new OllamaClient().interpret(text, capabilities, contextSummary, previousMessages);
            } catch (Exception exception) {
                error = exception.getMessage() == null ? exception.toString() : exception.getMessage();
            }
            OllamaClient.Plan completedPlan = plan;
            String completedError = error;
            if (!display.isDisposed()) {
                display.asyncExec(() -> finishRequest(page, diagramEditor, context, completedPlan, completedError));
            }
        }, "Archi-Assistant-Ollama");
        worker.setDaemon(true);
        worker.start();
    }

    private void finishRequest(IWorkbenchPage page, IArchimateDiagramEditor editor, CapabilityContext context,
            OllamaClient.Plan plan, String error) {
        if (status == null || status.isDisposed()) return;
        busy.set(false);
        submit.setEnabled(true);
        if (error != null) {
            addAssistantMessage("No se pudo completar: " + error);
            setStatus("No se pudo completar: " + error);
            return;
        }
        if (!"ready".equals(plan.status())) {
            addAssistantMessage(plan.question());
            setStatus("clarify".equals(plan.status())
                    ? "Necesito una aclaracion." : "Instruccion fuera del alcance actual.");
            return;
        }
        if (page.getActiveEditor() != editor) {
            String message = "El diagrama activo cambio mientras se procesaba la instruccion. Vuelve a enviarla.";
            addAssistantMessage(message);
            setStatus("No se pudo completar: " + message);
            return;
        }

        Capability capability = registry.get(plan.action());
        if (capability == null) {
            String message = "La capacidad seleccionada ya no esta registrada.";
            addAssistantMessage(message);
            setStatus("No se pudo completar: " + message);
            return;
        }
        try {
            Command command = capability.createCommand(context, plan.arguments());
            if (command == null || !command.canExecute()) {
                throw new IllegalStateException("La instruccion no cumple las condiciones para ejecutarse.");
            }
            CommandStack commandStack = editor.getGraphicalViewer().getEditDomain().getCommandStack();
            commandStack.execute(command);
            String message = capability.completionMessage(plan.arguments());
            addAssistantMessage(message);
            setStatus("completado...");
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.toString() : exception.getMessage();
            addAssistantMessage("No se pudo completar: " + message);
            setStatus("No se pudo completar: " + message);
        }
    }

    private void addAssistantMessage(String message) {
        history.add(Map.of("role", "assistant", "content", message));
        while (history.size() > MAX_HISTORY_MESSAGES) history.remove(0);
        appendTranscript("Asistente: " + message);
    }

    private void appendTranscript(String message) {
        if (transcript == null || transcript.isDisposed()) return;
        transcript.append("\n\n" + message);
        transcript.setSelection(transcript.getCharCount());
    }

    private void setStatus(String message) {
        if (status != null && !status.isDisposed()) {
            status.setText(message);
            status.getParent().layout(true, true);
        }
    }

    @Override
    public void setFocus() {
        if (instruction != null && !instruction.isDisposed()) instruction.setFocus();
    }
}
