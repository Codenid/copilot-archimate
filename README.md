# Asistente de diagramacion para Archi 5.7

La extension contribuye una vista acoplable junto a **Properties**. Ejecuta `iniciar.ajs` desde jArchi para abrirla. El arquitecto escribe instrucciones en el chat; Ollama selecciona una capacidad registrada y devuelve un plan estructurado. Solo la capacidad local validada modifica el modelo.

## Capacidades iniciales

- Crear cualquier tipo concreto de elemento ArchiMate presente en el metamodelo de la version activa de Archi, usando un catalogo dinamico (sin limitarse a Data Objects).
- Mover un elemento por coordenadas absolutas, desplazamiento o direccion.
- Ampliar un elemento un 25 % por defecto, indicar otro porcentaje o definir dimensiones.
- Cambiar el relleno, borde o color de texto de un elemento.
- Agregar o actualizar una propiedad.
- Conectar dos elementos visibles mediante una relacion ArchiMate admitida.
- Crear un elemento visualmente dentro de otro.
- Crear un Grouping que englobe visualmente un elemento existente.
- Crear varios elementos y anidarlos dentro de elementos nuevos o existentes en una instruccion.
- Crear Groupings que contengan varios elementos existentes o recien creados.
- Crear conexiones entre elementos existentes y recien creados en una misma instruccion.
- Redimensionar varios elementos existentes o definir dimensiones de los nuevos.

Las acciones sobre un elemento pueden usar la unica seleccion del diagrama o su nombre. Si el elemento no se identifica de forma unica, o faltan argumentos requeridos, el asistente pregunta en el chat. La contencion es visual: no inventa una relacion semantica. `enclose_element` crea un Grouping para un elemento; `compose_elements` permite crear estructuras con varios elementos, agrupaciones, conexiones y cambios de tamaño en un solo comando deshacible. Al agregar dentro de un contenedor, el asistente busca una posicion libre con margen, deja fijos los elementos existentes y amplia el contenedor solo si no hay espacio suficiente. Los elementos nuevos en el nivel principal se colocan debajo del contenido existente. Los miembros de un Grouping deben ser hermanos del mismo contenedor. Cada cambio se ejecuta como comando de Archi para permitir **Undo/Redo**. La interfaz no se bloquea mientras Ollama interpreta.

Ejemplo de instruccion compuesta: "Crea el actor Cliente y el componente Portal, agrupalos en Canales, conectalos mediante serving y cambia Portal a 500x180". Tambien se pueden pedir elementos nuevos dentro de contenedores existentes o de otros elementos nuevos.

La caja funciona como chat: **Enter** envia la instruccion y **Alt+Enter** inserta una nueva linea. El enrutador recibe en cada peticion los identificadores de todos los tipos concretos de elemento reconocidos por el metamodelo de la instalacion, evitando mantener una lista manual que se quede desactualizada.

La extension no ejecuta codigo generado por IA. Una instruccion que requiera una capacidad no registrada se informa como no soportada.

## Agregar una capacidad (plug and play)

Implementa `Capability` con un identificador estable, una descripcion con los argumentos requeridos, un metodo `createCommand(...)` que valida el contexto y devuelve un comando deshacible, y un mensaje de finalizacion. Registra esa clase en el extension point `com.archimatetool.asistentediagramacion.capabilities` del `plugin.xml`:

```xml
<extension point="com.archimatetool.asistentediagramacion.capabilities">
   <capability class="mi.plugin.MiNuevaCapacidad"/>
</extension>
```

Tambien se pueden registrar capacidades desde otro bundle OSGi, sin modificar el enrutador ni las capacidades existentes.

El ejecutor conoce unicamente la interfaz `Capability`. El enrutador ofrece al modelo de IA las descripciones e IDs registrados; valida que la respuesta solo seleccione IDs existentes y que los argumentos sean cadenas. La implementacion de cada capacidad conserva la validacion especifica de ArchiMate.

## Requisitos

- Archi 5.7.0 y Java 21.
- jArchi habilitado en Archi para abrir la vista con el script.
- Ollama local en `http://localhost:11434`, con al menos un modelo instalado.
- Un diagrama ArchiMate activo para ejecutar cambios.

## Compilar e instalar

1. Cierra Archi.
2. Desde PowerShell, ejecuta `.\build.ps1`. Si Archi esta instalado en otra carpeta, pasa su ruta: `.\build.ps1 -ArchiHome 'D:\Apps\Archi'`.
3. Inicia Archi.
4. Ejecuta `iniciar.ajs` desde el administrador de scripts jArchi.

El script compila el bundle OSGi, lo copia a `%APPDATA%\Archi\dropins` y deja el JAR de salida en `build`. Se requiere reiniciar Archi para cargar cambios del plugin. La instalacion no toca otras carpetas o scripts.

## Limites iniciales

- Cada instruccion invoca una capacidad registrada. `compose_elements` permite combinar creacion, contencion, agrupacion, conexiones y redimensionamiento; sus miembros de Grouping deben pertenecer al mismo contenedor y no se admite anidar Groupings entre si.
- `connect_elements` ofrece el conjunto de relaciones implementado por la capacidad y consulta la matriz de compatibilidad ArchiMate del modelo antes de ejecutar.
- `add_inside_element` agrega una figura visual anidada; no crea una relacion de composicion.
- El area de trabajo es el diagrama activo. Los elementos fuera del diagrama activo no se consideran candidatos visuales.
- Los mensajes de ejecucion aparecen en la vista. No se envia el contenido del modelo a servicios externos; las solicitudes van a Ollama local.
