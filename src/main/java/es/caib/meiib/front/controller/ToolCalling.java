package es.caib.meiib.front.controller;

import es.caib.meiib.service.interfaces.*;

import es.caib.meiib.service.models.CustomStyle;
import es.caib.meiib.service.models.DadesObertes.*;
import es.caib.meiib.service.models.Finance;
import es.caib.meiib.service.models.LiniaEstrategica;
import es.caib.meiib.service.models.Municipi;
import es.caib.meiib.service.models.agentIA.*;
import es.caib.meiib.service.reports.ActuacioGroupByLiniaEstrategicaReport;
import es.caib.meiib.service.reports.InversioReport;
import es.caib.meiib.service.reports.ProgramatByLiniaReport;
import es.caib.meiib.service.utils.FiltreConsulta;



import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.util.*;
import java.util.stream.Collectors;

public class ToolCalling {

    private static final String F_LINEAS = "lineas";
    private static final String F_AMBITOS = "ambitos";
    private static final String F_MUNICIPIOS = "municipios";
    private static final String F_ACTUACIONES = "actuaciones";
    private static final String F_FINANCE = "fuentes";
    private static final String F_FINANCE_DETAIL = "fuente-programado";
    private static final String F_PROGRAMADO_GROUPBY_LINEA = "programado-linea";
    private static final String F_MOBILIZADO_GROUPBY_LINEA = "movilizado-linea";

    private static final String F_GRAFICO = "grafico";

    private static final int PAGE_SIZE = 20;
    private static final int PAGE = 1;

    private IAmbitService ambitServiceBean;
    private IMunicipiService municipiServiceBean;
    private IActuacioService actuacioServiceBean;
    private IFinanceService financeServiceBean;
    private ILiniaEstrategicaService lineaServiceBean;
    private IReportService reportServiceBean;
    private ICustomStyleService customStyleServiceBean;

    public ToolCalling() {}

    public ToolCalling(IAmbitService ambitServiceBean, IMunicipiService municipiServiceBean,
                       IActuacioService actuacioServiceBean, IFinanceService financeServiceBean,
                       ILiniaEstrategicaService lineaServiceBean, IReportService reportServiceBean,
                       ICustomStyleService customStyleServiceBean) {
        this.ambitServiceBean = ambitServiceBean;
        this.municipiServiceBean = municipiServiceBean;
        this.actuacioServiceBean = actuacioServiceBean;
        this.financeServiceBean = financeServiceBean;
        this.lineaServiceBean = lineaServiceBean;
        this.reportServiceBean = reportServiceBean;
        this.customStyleServiceBean = customStyleServiceBean;

    }
    // Lista de operaciones disponibles para Tool-Chain
    public List<OllamaTool> obtenerFuncionesAPIDatosAbiertos() {

        OllamaTool ollamaToolLineas = this.generaFuncion(F_LINEAS,"Ejecuta esta herramienta OBLIGATORIAMENTE para devolver información sobre las líneas estratégicas del MEIIB30. IMPORTANTE: No confundas las líneas estratégicas con los ámbitos o los ejes del MEIIB30.", generaParametros(F_LINEAS));
        OllamaTool ollamaToolMovilizadoLinea = this.generaFuncion(F_MOBILIZADO_GROUPBY_LINEA,"Ejecuta esta herramienta OBLIGATORIAMENTE para obtener información sobre ejecución de líneas estratégicas, número de proyectos/actuaciones/inversión y su importe total agrupado por líneas estratégicas.  IMPORTANTE: No confundas las líneas estratégicas con los ámbitos o los ejes del MEIIB30.", generaParametros(F_MOBILIZADO_GROUPBY_LINEA));
        OllamaTool ollamaToolProgramadoLinea = this.generaFuncion(F_PROGRAMADO_GROUPBY_LINEA, "Ejecuta esta herramienta OBLIGATORIAMENTE para obtener información sobre programación agrupada por líneas estratégicas, número de programas y su importe total agrupado por líneas estratégicas. IMPORTANTE: No confundas las líneas estratégicas con los ámbitos o los ejes del MEIIB30.", generaParametros(F_PROGRAMADO_GROUPBY_LINEA));
        OllamaTool ollamaToolFinance = this.generaFuncion(F_FINANCE,"Ejecuta esta herramienta OBLIGATORIAMENTE cuando el usuario pregunte explícitamente por 'fuentes de financiación' o necesites obtener el código/ID de una fuente para utilizarlo en otra transacción.", generaParametros(F_FINANCE));
        OllamaTool ollamaToolProgramadoFuente = this.generaFuncion(F_FINANCE_DETAIL, "Ejecuta esta herramienta OBLIGATORIAMENTE para obtener los programas (desglose de la programación) de una fuente de financiación determinada.", generaParametros(F_FINANCE_DETAIL));
        OllamaTool ollamaToolAmbitos = this.generaFuncion(F_AMBITOS,"Ejecuta esta herramienta OBLIGATORIAMENTE si la pregunta del usuario contiene la palabra 'ámbito' o 'ámbitos'. IMPORTANTE: No confundas los ámbitos con los ejes (Competitividad, Sostenibilidad, Cohesión). No intentes adivinar la respuesta ni uses tu memoria, extrae la lista oficial invocando esta función.",generaParametros(F_AMBITOS));
        OllamaTool ollamaToolMunicipios = this.generaFuncion(F_MUNICIPIOS,"Ejecuta esta herramienta OBLIGATORIAMENTE sólo cuando la pregunta del usuario mencione un municipio específico y necesites averiguar su código para pasarlo como parámetro a otras transacciones.", generaParametros(F_MUNICIPIOS));
        OllamaTool ollamaToolActuaciones = this.generaFuncion(F_ACTUACIONES, "Obtiene la lista de actuaciones (también llamados proyectos) del MEIIB30. REGLAS ESTRICTAS DE USO:\\n1. Parámetro OBLIGATORIO (idFinance): ANTES de invocar esta función, DEBES ejecutar obligatoriamente la función 'fuentes' para extraer el ID exacto.\\n2. Parámetro OPCIONAL (codMunicipi): Si la consulta incluye un municipio, DEBES ejecutar previamente la función 'municipios' para obtener su código exacto.\\n3. PROHIBICIÓN ABSOLUTA: NUNCA inventes, infieras o asumas ningún ID o código. Si te falta alguno, utiliza las funciones previas para averiguarlo.",generaParametros(F_ACTUACIONES));
        OllamaTool ollamaToolGrafico = this.generaFuncion(F_GRAFICO,"Ejecuta esta herramienta OBLIGATORIAMENTE para transformar datos en un gráfico (barras, tarta, donut). REGLA ESTRICTA: ESTÁ PROHIBIDO invocar esta función sin tener los datos reales. Si el usuario pide un gráfico sobre fuentes o actuaciones, DEBES ejecutar primero las funciones 'fuentes' o 'actuaciones' para obtener los importes exactos. NUNCA inventes los números de las etiquetas o los datos.", generaParametros(F_GRAFICO));

        ArrayList<OllamaTool> tools = new ArrayList<>();
        tools.add(ollamaToolLineas);
        tools.add(ollamaToolMovilizadoLinea);
        tools.add(ollamaToolProgramadoLinea);
        tools.add(ollamaToolProgramadoFuente);
        tools.add(ollamaToolAmbitos);
        tools.add(ollamaToolMunicipios);
        tools.add(ollamaToolFinance);
        tools.add(ollamaToolActuaciones);
        tools.add(ollamaToolGrafico);

        return tools;
    }

    private OllamaTool generaFuncion(String nom, String descripcio, OllamaToolFunctionParameters parameters) {
        OllamaTool ollamaTool = new OllamaTool();
        ollamaTool.setType("function");
        OllamaToolFunction ollamaToolFunction = new OllamaToolFunction();
        ollamaToolFunction.setName(nom);
        ollamaToolFunction.setDescription(descripcio);
        ollamaToolFunction.setParameters(parameters);
        ollamaTool.setFunction(ollamaToolFunction);
        return ollamaTool;
    }

    private OllamaToolFunctionParameters generaParametros(String funcion) {
        OllamaToolFunctionParameters ollamaToolFunctionParameters = new OllamaToolFunctionParameters();

        if (funcion.equals(F_MUNICIPIOS)) {
            Map<String, OllamaToolFunctionParameter> params = new HashMap<>();

            // Parámetro de filtro obligatorio: nombre del municipio (definido por el usuario)
            OllamaToolFunctionParameter parameterNombreMunicipio = new OllamaToolFunctionParameter();
            parameterNombreMunicipio.setType("string");
            parameterNombreMunicipio.setDescription("Nombre exacto del municipio extraído de la consulta, sin artículos ni texto adicional (ej. 'Palma', 'Ibiza', 'Ciutadella'). Obligatorio.");
            params.put("municipi",parameterNombreMunicipio);

            ollamaToolFunctionParameters.setProperties(params);
            ollamaToolFunctionParameters.setType("object");
            ollamaToolFunctionParameters.setRequired(List.of("municipi"));

        }

        if (funcion.equals(F_FINANCE_DETAIL)) {
            Map<String, OllamaToolFunctionParameter> params = new HashMap<>();

            // Parámetro de filtro obligatorio: id de la fuente de financiación
            OllamaToolFunctionParameter parameterIdFuente = new OllamaToolFunctionParameter();
            parameterIdFuente.setType("string");
            parameterIdFuente.setDescription("Id de la fuente de financiación obtenida en fuentes. Obligatorio.");
            params.put("idFuente",parameterIdFuente);

            ollamaToolFunctionParameters.setProperties(params);
            ollamaToolFunctionParameters.setType("object");
            ollamaToolFunctionParameters.setRequired(List.of("idFuente"));
        }
        if (funcion.equals(F_ACTUACIONES)) {

            Map<String, OllamaToolFunctionParameter> params = new HashMap<>();
            // Parámetro de filtro opcional: código de municipio
            OllamaToolFunctionParameter parameterDescriptionMunicipio = new OllamaToolFunctionParameter();
            parameterDescriptionMunicipio.setType("string");
            parameterDescriptionMunicipio.setDescription("Código del municipio obtenido de la herramienta 'municipios'. Opcional.");
            params.put("codMunicipi",parameterDescriptionMunicipio);

            // Parámetro de filtro obligatorio: fuente de financiación
            OllamaToolFunctionParameter parameterDescriptionFuente = new OllamaToolFunctionParameter();
            parameterDescriptionFuente.setType("string");
            parameterDescriptionFuente.setDescription("[ID FINANCE] de la fuente de financiación obtenido de la herramienta 'fuentes'. Es un número. Obligatorio.");
            params.put("idFinance",parameterDescriptionFuente);

            ollamaToolFunctionParameters.setProperties(params);
            ollamaToolFunctionParameters.setType("object");
            ollamaToolFunctionParameters.setRequired(List.of("idFinance"));

        }

        if (funcion.equals(F_GRAFICO)) {

            Map<String, OllamaToolFunctionParameter> params = new HashMap<>();

            // Parámetro de título y tipo de gráfico
            OllamaToolFunctionParameter parameterTipoGrafico = new OllamaToolFunctionParameter();
            parameterTipoGrafico.setType("string");
            parameterTipoGrafico.setDescription("Tipo de gráfico: pie (tarta) | bar (barras) | doughnut (donut). Obligatorio.");
            params.put("tipo",parameterTipoGrafico);

            OllamaToolFunctionParameter parameterTituloGrafico = new OllamaToolFunctionParameter();
            parameterTituloGrafico.setType("string");
            parameterTituloGrafico.setDescription("Título del gráfico. Obligatorio");
            params.put("titulo",parameterTituloGrafico);

            OllamaToolFunctionArrayParameter parameterEtiquetas = new OllamaToolFunctionArrayParameter();
            parameterEtiquetas.setDescription("Array con los nombres o códigos de las categorias. Obligatorio.");
            parameterEtiquetas.setItems(new OllamaToolFunctionItems("string"));
            params.put("etiquetas", parameterEtiquetas);

            OllamaToolFunctionArrayParameter parameterDatos = new OllamaToolFunctionArrayParameter();
            parameterDatos.setDescription("Array con los valores integer or double. Obligatorio.");
            parameterDatos.setItems(new OllamaToolFunctionItems("number"));
            params.put("valores", parameterDatos);

            ollamaToolFunctionParameters.setProperties(params);
            ollamaToolFunctionParameters.setType("object");
            ollamaToolFunctionParameters.setRequired(List.of("tipo", "titulo", "etiquetas", "valores"));

        }
        return ollamaToolFunctionParameters;
    }

    public String toolChain(String funcionAEjecutar, Map<String, Object> params) {

        // Si nos piden llamada a API, la ejecutamos
        if (funcionAEjecutar.equals(F_LINEAS)) {
            List<LiniaEstrategicaDadesObertes> lineas = this.lineaServiceBean.getLiniesEstrategiquesDadesObertes(null);
            return generaTextoRespuesta(lineas);
        }
        if (funcionAEjecutar.equals(F_MOBILIZADO_GROUPBY_LINEA)) {
            List<ActuacioGroupByLiniaEstrategicaReport> infoLineas = this.reportServiceBean.getActuacionsGroupByLinia();
            return generaTextoRespuesta(infoLineas);
        }

        if (funcionAEjecutar.equals(F_PROGRAMADO_GROUPBY_LINEA)) {
            List<ProgramatByLiniaReport> infoLineas = this.reportServiceBean.getProgramatByLinia();
            return generaTextoRespuesta(infoLineas);
        }

        if (funcionAEjecutar.equals(F_FINANCE)) {
            List<FinanceDadesObertes> finances = this.financeServiceBean.getFinancesDadesObertes();
            return generaTextoRespuesta(finances);
        }

        if (funcionAEjecutar.equals(F_FINANCE_DETAIL)) {
            String idFinance = extraerParametroString("idFuente", params);
            FinanceDescriptionDadesObertes f = this.financeServiceBean.getFinanceDescriptionDadesObertes(Long.parseLong(idFinance));
            return f.toString();
        }
        if  (funcionAEjecutar.equals(F_AMBITOS)) {
            List<AmbitDadesObertes> ambits = this.ambitServiceBean.getAmbitsDadesObertes(null);
            return generaTextoRespuesta(ambits);
        }

        if (funcionAEjecutar.equals(F_MUNICIPIOS)) {
            String municipi = extraerParametroString("municipi", params);
            List<Municipi> municipis = this.municipiServiceBean.cercaMunicipi(municipi);
            return generaTextoRespuesta(municipis);
        }


        if (funcionAEjecutar.equals(F_GRAFICO)) {
            String tipo;
            String titulo;
            List<String> etiquetas;
            List<Number> valores;

            if (params!=null) {
                tipo = extraerParametroString("tipo", params);
                titulo = extraerParametroString("titulo",params);
                etiquetas = extraerParametroArrayString("etiquetas", params);
                valores = extraerParametroArrayNumber("valores", params);
            }
            else {
                tipo = "bar";
                titulo = "unnamed";
                etiquetas = new ArrayList<>();
                valores = new ArrayList<>();
            }

            return generaGrafico(tipo,titulo,etiquetas,valores);
        }

        if (funcionAEjecutar.equals(F_ACTUACIONES)) {
            ArrayList<FiltreConsulta> filtros = new ArrayList<>();

            if (params!=null) {
                for (String s : params.keySet()) {
                    FiltreConsulta filtro = new FiltreConsulta();
                    filtro.setCamp(s);
                    filtro.setValor(params.get(s));
                    filtros.add(filtro);
                }
            }

            List<ActuacioDadesObertes> actuaciones = this.actuacioServiceBean.getActuacionsDadesObertes(PAGE_SIZE,PAGE,filtros);

            return generaTextoRespuesta(actuaciones);
        }
        return "Función de ToolChain no implementada: " + funcionAEjecutar;
    }

    // Generamos la respuesta para ToolChain - lista actuaciones
    private String generaTextoRespuesta(List<?> lista) {
        if (lista==null || lista.isEmpty()){
            return "";
        }

        return lista.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("\n---\n"));
    }

    // Generación dinámica del gráfico
    public String generaGrafico(String tipo, String titulo, List<String> etiquetas, List<Number> datos) {

        // Estilos de gráficos del MEIIB30 en BBDD
        CustomStyle.TypeStyle customStyleType;

        switch (tipo.toLowerCase()) {
            case "pie":     customStyleType = CustomStyle.TypeStyle.PIE;   break;
            case "doughnut": customStyleType = CustomStyle.TypeStyle.DONUT; break;
            default:        customStyleType = CustomStyle.TypeStyle.CHART;  break;
        }

        CustomStyle customStyle = this.customStyleServiceBean.getDefaultCustomStyle(customStyleType);

        // Array de etiquetas
        JsonArrayBuilder labelsBuilder = Json.createArrayBuilder();
        etiquetas.forEach(labelsBuilder::add);

        // Array de datos, Number puede ser Integer, Double
        JsonArrayBuilder dataBuilder = Json.createArrayBuilder();
        datos.forEach(num -> {
            if (num instanceof Double) dataBuilder.add((Double) num);
            else if (num instanceof Integer) dataBuilder.add((Integer) num);
            else dataBuilder.add(num.doubleValue());
        });

        // Generamos JSON para cada tipo de gráfico
        switch (tipo.toLowerCase()) {
            case "pie":      return generaGraficoPie(titulo, labelsBuilder, dataBuilder, customStyle, datos.size());
            case "doughnut": return generaGraficoDonut(titulo, labelsBuilder, dataBuilder, customStyle, datos.size());
            default:         return generaGraficoBar(titulo, labelsBuilder, dataBuilder, customStyle);
        }
    }

    private String generaGraficoBar(String titulo, JsonArrayBuilder labelsBuilder, JsonArrayBuilder datasetBuilder, CustomStyle customStyle) {

        // Array de colores
        JsonArrayBuilder colorsBuilder = Json.createArrayBuilder();
        for (String color : customStyle.getArrayColor().split(";")) {
            colorsBuilder.add(color.trim());
        }

        JsonObject chartConfig = Json.createObjectBuilder()
                .add("type", "bar")
                .add("data", Json.createObjectBuilder()
                        .add("labels", labelsBuilder)
                        .add("datasets", Json.createArrayBuilder()
                                .add(Json.createObjectBuilder()
                                        .add("data", datasetBuilder)
                                        .add("backgroundColor", colorsBuilder)
                                        .add("borderColor", customStyle.getBorderColor())
                                        .add("borderWidth", 1)
                                        .add("borderRadius", 6)
                                        .add("borderSkipped", "bottom")
                                        .add("barPercentage", 0.7)
                                )
                        )
                )
                .add("options", Json.createObjectBuilder()
                        .add("responsive", true)
                        .add("maintainAspectRatio", false)
                        .add("layout", Json.createObjectBuilder()
                                .add("padding", Json.createObjectBuilder()
                                        .add("top", 35).add("left", 10)
                                        .add("right", 20).add("bottom", 25)
                                )
                        )
                        .add("plugins", Json.createObjectBuilder()
                                .add("legend", Json.createObjectBuilder()
                                        .add("display", false)
                                )
                                .add("title", Json.createObjectBuilder()
                                        .add("display", true)
                                        .add("text", titulo)
                                        .add("color", customStyle.getFontColor())
                                        .add("font", Json.createObjectBuilder()
                                                .add("family", customStyle.getFontFamily())
                                                .add("size", customStyle.getFontSize2())
                                                .add("weight", customStyle.getFontWeight())
                                                .add("style", "normal")
                                        )
                                        .add("padding", Json.createObjectBuilder()
                                                .add("top", 10).add("bottom", 40)
                                        )
                                )
                                .add("datalabels", Json.createObjectBuilder()
                                        .add("anchor", "end")
                                        .add("align", "top")
                                        .add("offset", 5)
                                        .add("color", customStyle.getFontColor())
                                        .add("font", Json.createObjectBuilder()
                                                .add("family", customStyle.getFontFamily())
                                                .add("size", customStyle.getFontSize())
                                                .add("weight", "bold")
                                        )
                                )
                        )
                        .add("scales", Json.createObjectBuilder()
                                .add("x", Json.createObjectBuilder()
                                        .add("border", Json.createObjectBuilder()
                                                .add("display", false)
                                        )
                                        .add("grid", Json.createObjectBuilder()
                                                .add("display", false)
                                                .add("drawBorder", false)
                                        )
                                        .add("ticks", Json.createObjectBuilder()
                                                .add("color", customStyle.getFontColor())
                                                .add("font", Json.createObjectBuilder()
                                                        .add("size", customStyle.getFontSize2())
                                                        .add("family", customStyle.getFontFamily())
                                                        .add("weight", customStyle.getFontWeight())
                                                )
                                        )
                                )
                                .add("y", Json.createObjectBuilder()
                                        .add("display", false)
                                        .add("grid", Json.createObjectBuilder()
                                                .add("display", false)
                                        )
                                )
                        )
                )
                .build();

        return chartConfig.toString();
    }

    private String generaGraficoPie(String titulo, JsonArrayBuilder labelsBuilder, JsonArrayBuilder datasetBuilder, CustomStyle customStyle, int numDatos) {

        String[] colors = customStyle.getArrayColor().split(";");
        int numColors = colors.length;

        JsonArrayBuilder colorsBuilder = Json.createArrayBuilder();
        for (int i = 0; i < numDatos; i++) {
            colorsBuilder.add(colors[i % numColors].trim());
        }

        JsonObject chartConfig = Json.createObjectBuilder()
                .add("type", "pie")
                .add("data", Json.createObjectBuilder()
                        .add("labels", labelsBuilder)
                        .add("datasets", Json.createArrayBuilder()
                                .add(Json.createObjectBuilder()
                                        .add("data", datasetBuilder)
                                        .add("backgroundColor", colorsBuilder)
                                        .add("borderColor", customStyle.getBorderColor())
                                        .add("borderWidth", 2)
                                )
                        )
                )
                .add("options", Json.createObjectBuilder()
                        .add("responsive", true)
                        .add("maintainAspectRatio", true)
                        .add("layout", Json.createObjectBuilder()
                                .add("padding", Json.createObjectBuilder()
                                        .add("top", 40)
                                        .add("bottom", 30)
                                        .add("left", 10)
                                        .add("right", 10)
                                )
                        )
                        .add("plugins", Json.createObjectBuilder()
                                .add("legend", Json.createObjectBuilder()
                                        .add("position", "right")
                                        .add("labels", Json.createObjectBuilder()
                                                .add("boxWidth", 12)
                                                .add("color", customStyle.getFontColor())
                                                .add("font", Json.createObjectBuilder()
                                                        .add("size", customStyle.getFontSize())
                                                        .add("family", customStyle.getFontFamily())
                                                )
                                        )
                                )
                                .add("title", Json.createObjectBuilder()
                                        .add("display", true)
                                        .add("text", titulo)
                                        .add("color", customStyle.getFontColor())
                                        .add("font", Json.createObjectBuilder()
                                                .add("family", customStyle.getFontFamily())
                                                .add("size", customStyle.getFontSize2().intValue())
                                                .add("weight", customStyle.getFontWeight())
                                        )
                                        .add("padding", Json.createObjectBuilder()
                                                .add("top", 10)
                                                .add("bottom", 20)
                                        )
                                )
                                .add("datalabels", Json.createObjectBuilder()
                                        .add("anchor", "center")
                                        .add("align", "center")
                                        .add("display", true)
                                        .add("textAlign", "center")
                                        .add("padding", 6)
                                        .add("color", customStyle.getFontColor())
                                        .add("formatter", "function(value, context) { " +
                                                "const total = context.chart.data.datasets[0].data.reduce((a, b) => a + b, 0); " +
                                                "const percentage = ((value / total) * 100).toFixed(1); " +
                                                "return percentage + '%'; " +
                                                "}"
                                        )
                                        .add("font", Json.createObjectBuilder()
                                                .add("family", customStyle.getFontFamily())
                                                .add("size", customStyle.getFontSize())
                                                .add("weight", customStyle.getFontWeight())
                                        )
                                )
                        )
                )
                .build();
        return chartConfig.toString();
    }

    private String generaGraficoDonut(String titulo, JsonArrayBuilder labelsBuilder, JsonArrayBuilder dataBuilder, CustomStyle customStyle, int numDatos) {

        String[] colors = customStyle.getArrayColor().split(";");
        int numColors = colors.length;

        JsonArrayBuilder colorsBuilder = Json.createArrayBuilder();
        for (int i = 0; i < numDatos; i++) {
            colorsBuilder.add(colors[i % numColors].trim());
        }

        JsonObject chartConfig = Json.createObjectBuilder()
                .add("type", "doughnut")
                .add("data", Json.createObjectBuilder()
                        .add("labels", labelsBuilder)
                        .add("datasets", Json.createArrayBuilder()
                                .add(Json.createObjectBuilder()
                                        .add("data", dataBuilder)
                                        .add("backgroundColor", colorsBuilder)
                                        .add("borderColor", customStyle.getBorderColor())
                                        .add("borderWidth", 2)
                                        .add("hoverOffset", 15)
                                )
                        )
                )
                .add("options", Json.createObjectBuilder()
                        .add("responsive", true)
                        .add("maintainAspectRatio", false)
                        .add("cutout", "60%")
                        .add("layout", Json.createObjectBuilder()
                                .add("padding", Json.createObjectBuilder()
                                        .add("top", 20).add("bottom", 30)
                                        .add("left", 150).add("right", 150)
                                )
                        )
                        .add("plugins", Json.createObjectBuilder()
                                .add("legend", Json.createObjectBuilder()
                                        .add("display", false)
                                )
                                .add("title", Json.createObjectBuilder()
                                        .add("display", true)
                                        .add("align", "start")
                                        .add("text", titulo)
                                        .add("color", customStyle.getFontColor())
                                        .add("font", Json.createObjectBuilder()
                                                .add("family", customStyle.getFontFamily())
                                                .add("size", customStyle.getFontSize2())
                                                .add("weight", customStyle.getFontWeight())
                                        )
                                        .add("padding", Json.createObjectBuilder()
                                                .add("top", 0).add("bottom", 20)
                                        )
                                )
                                .add("datalabels", Json.createObjectBuilder()
                                        .add("anchor", "center")
                                        .add("align", "center")
                                        .add("offset", 0)
                                        .add("display", true)
                                        .add("textAlign", "center")
                                        .add("color", customStyle.getFontColor())
                                        .add("font", Json.createObjectBuilder()
                                                .add("family", customStyle.getFontFamily())
                                                .add("size", customStyle.getFontSize())
                                                .add("weight", customStyle.getFontWeight())
                                        )
                                )
                        )
                )
                .build();

        return chartConfig.toString();
    }

    private String extraerParametroString(String param, Map<String,Object> params) {

        if (params != null && params.containsKey(param)) {
            Object valor = params.get(param);

            if (valor instanceof String) {
                return (String) valor;
            } else if (valor!=null) {
                return String.valueOf(valor);
            }
        }
        return "";
    }


    private List<String> extraerParametroArrayString(String param, Map<String,Object> params) {

        if (params!=null && params.containsKey(param)) {
            Object valor = params.get(param);

            if (valor instanceof List) {
                List<?> listaCruda = (List<?>) valor;

                return listaCruda.stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());

            } else if (valor != null) {
                return new ArrayList<>(Collections.singleton(String.valueOf(valor)));
            }
        }
        return new ArrayList<>();
    }

    private List<Number> extraerParametroArrayNumber(String param, Map<String,Object> params) {
        if (params != null && params.containsKey(param)) {
            Object valor = params.get(param);

            if (valor instanceof List) {
                List<?> listaCruda = (List<?>) valor;

                return listaCruda.stream()
                        .map(item -> {
                            // Si ya es un número nativo (Double, Integer, etc.), lo casteamos
                            if (item instanceof Number) {
                                return (Number) item;
                            }
                            // Si el LLM lo ha mandado como String ("125.50"), lo convertimos a Double
                            else if (item instanceof String) {
                                try {
                                    return Double.valueOf((String) item);
                                } catch (NumberFormatException e) {
                                    return 0.0; // Fallback si manda texto que no es número
                                }
                            }
                            return 0.0; // Fallback para tipos desconocidos
                        })
                        .collect(Collectors.toList());

            } else if (valor instanceof Number) {
                // Por si manda un solo número fuera del array
                return new ArrayList<>(Collections.singletonList((Number) valor));
            }
        }
        return new ArrayList<>();
    }
}
