package es.caib.meiib.front.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import es.caib.meiib.service.interfaces.*;
import es.caib.meiib.service.models.agentIA.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;



@Path("/chat")
@RequestScoped
public class ChatStreamRestEndPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStreamRestEndPoint.class);

    @Inject
    private ChatController chatSession;

    @Inject
    private IAmbitService ambitServiceBean;

    @Inject
    private IMunicipiService municipiServiceBean;

    @Inject
    private IActuacioService actuacioServiceBean;

    @Inject
    private IFinanceService financeServiceBean;

    @Inject
    private ILiniaEstrategicaService lineaServiceBean;

    @Inject
    private IReportService reportServiceBean;

    @Inject
    private ICustomStyleService customStyleServiceBean;

    @POST
    @Path("/stream")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/plain; charset=UTF-8")
    public Response streamRespuesta(String peticionUsuario) {
        OllamaChatRequest conversacionActual = chatSession.getOllamaChatRequest();
        StreamingOutput stream = outputStream -> {
            try {

                // Búsqueda RAG
                String contextoRag = obtenerContenidoSimilar(peticionUsuario,
                        this.chatSession.getAgentIA().getUrlAlejandria(),
                        this.chatSession.getTokenAlejandria(),
                        this.chatSession.getAgentIA().getRagMaxItems(),
                        this.chatSession.getAgentIA().getRagMinScoreLimit());

                OllamaMessage ollamaNextMessage;

                if (contextoRag==null || contextoRag.isEmpty())
                    ollamaNextMessage = new OllamaMessage("user", peticionUsuario);
                else
                    ollamaNextMessage = new OllamaMessage("user", "### CONTEXTO RAG:\n" + contextoRag + "\n\n### PREGUNTA:\n" + peticionUsuario);

                this.chatSession.getOllamaChatRequest().getMessages().add(ollamaNextMessage); // Mensaje que inicia la conversación

                boolean mensajePendiente = true;
                while (mensajePendiente) {
                    OllamaResponseWrapper ollamaResponseWrapper = procesarMensajePendiente(this.chatSession.getOllamaChatRequest(), outputStream);
                    // Ollama nos pide lanzar transacción API para completar respuesta
                    if (ollamaResponseWrapper.getToolCalls()!=null &&
                            ollamaResponseWrapper.getToolCalls().size()>0) {

                        // Petición ToolChain
                        // Anotamos la respuesta de ollama (petición de toolchain) en el histórico de mensajes
                        this.chatSession.getOllamaChatRequest().getMessages().add(new OllamaMessageAssistant("assistant", "", ollamaResponseWrapper.getToolCalls())); // Al carro de respostes afegim la resposta a on ens demana un tool_chain

                        // Ejecutamos la(s) llamadas que nos piden y añadimos la respuesta como mensajes pendientes
                        ToolCalling toolCalling = new ToolCalling(this.ambitServiceBean,
                                                                  this.municipiServiceBean,
                                                                  this.actuacioServiceBean,
                                                                  this.financeServiceBean,
                                                                  this.lineaServiceBean,
                                                                  this.reportServiceBean,
                                                                  this.customStyleServiceBean);
                        for (OllamaToolCall tc : ollamaResponseWrapper.getToolCalls()) {
                            outputStream.write(("<message>"+ tc.getFunction().getName() + "</message>").getBytes());
                            outputStream.flush();


                            String content = toolCalling.toolChain(tc.getFunction().getName(), tc.getFunction().getArguments()); // Lanzamos la transacción que nos pide

                            // Preparamos la siguiente petición a ollama con la respuesta de la transacción que nos pidió ejecutar
                            this.chatSession.getOllamaChatRequest().getMessages().add(new OllamaMessageToolChain("tool",tc.getId(), content));
                        }

                        mensajePendiente = true;
                    }
                    // Ollama nos da una respuesta final al usuario (la cuál hemos ido mostrando a través del outputStream
                    else {

                        ollamaNextMessage.setContent(peticionUsuario); // Para mantener el històrico sin el RAG -> al último mensaje le eliminamos el RAG
                        this.chatSession.getOllamaChatRequest().getMessages().add(new OllamaMessage("assistant", ollamaResponseWrapper.getRespuestaFinalUsuario()));
                        LOGGER.info("Respuesta LLM generada. Tokens / seg: " + ollamaResponseWrapper.getTokensPorSegundo());
                        mensajePendiente = false;

                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        return Response.ok(stream).build();
    }

    // Procesa los mensajes pendientes.
    // Le pasamos por parámetro el outputStream por si la respuesta es de texto hacia el usuario ir haciendo el efecto de máquina de scribir
    private OllamaResponseWrapper procesarMensajePendiente(OllamaChatRequest ollamaChatRequest, OutputStream outputStream) throws IOException, InterruptedException {

        OllamaResponseWrapper respuesta = new OllamaResponseWrapper();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        String jsonOllamaChatRequest = objectMapper.writeValueAsString(ollamaChatRequest);

        LOGGER.info("Petición ollama: " + jsonOllamaChatRequest);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(this.chatSession.getAgentIA().getUrlEndpoint()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonOllamaChatRequest));

        if (this.chatSession.getAgentIA().getBearerTokenEndpoint() != null && !this.chatSession.getAgentIA().getBearerTokenEndpoint().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + this.chatSession.getAgentIA().getBearerTokenEndpoint());
        }

        HttpRequest request = requestBuilder.build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode()!=200) {
            String strErrorHttp = "[ERROR] Ollama ha respondido error " + response.statusCode() + ", error: " + getErrorHttpOllama(response);
            LOGGER.error(strErrorHttp);
            outputStream.write(strErrorHttp.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            respuesta.setRespuestaFinalUsuario(strErrorHttp);
            respuesta.setEsRespuestaFinal(true);
            respuesta.setTokensPorSegundo(0);
            return respuesta;
        }

        respuesta.setEsRespuestaFinal(true); // inicialmente suponemos que nos llegará respuesta para el usuario
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

            StringBuilder respuestaCompletaOllama = new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {
                LOGGER.info("ollama dice: " + line);
                OllamaChatResponse res = objectMapper.readValue(line, OllamaChatResponse.class);

                // Documentación de Ollama: en la última respuesta mediante /api/chat con stream: true (nuestro caso)
                // es donde se obtiene un done_reason con posibles valores {stop, length, load}
                if (res.getDoneReason()!=null) {
                    if ("load".equals(res.getDoneReason())) {
                        outputStream.write("[BUSY] Modelo ocupado espere unos minutos, por favor.".getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                        respuesta.setEsRespuestaFinal(true);
                        respuestaCompletaOllama.append("[BUSY] Modelo ocupado espere unos minutos, por favor.");
                        continue;
                    }
                    if ("length".equals(res.getDoneReason())) {
                        outputStream.write("[LENGHT] Se alcanzó el límite máximo de tokens en esta conversación.".getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                        respuesta.setEsRespuestaFinal(true);
                        respuestaCompletaOllama.append("[LENGHT] Se alcanzó el límite máximo de tokens en esta conversación.");
                        continue;
                    }
                    if ("stop".equals(res.getDoneReason())) {
                        respuesta.setTokensPorSegundo(res.obtenerTokensPorSegundo());
                        if (respuesta.getTokensPorSegundo() > 0)
                            outputStream.write(("\n\n[Tokens / Seg: ] " +  respuesta.getTokensPorSegundo()).getBytes(StandardCharsets.UTF_8));
                        respuesta.setEsRespuestaFinal(true);
                        continue;
                    }
                }

                // Respuesta de Ollama en el nodo message
                if (res.getMessage()!=null) {

                    OllamaMessageResponse message = res.getMessage();
                    // Solo hacemos streaming si hay contenido por processar
                    if (message.getContent()!=null && !message.getContent().isEmpty())
                    {
                        outputStream.write(message.getContent().getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                        respuesta.setEsRespuestaFinal(true);
                        respuestaCompletaOllama.append(message.getContent());
                    }
                    // Si hay ToolChain indicamos que es necesario hacer las llamadas que nos piden
                    /*if (message.getToolCalls()!=null) {
                        respuesta.setEsRespuestaFinal(false);
                        respuesta.setToolCalls(message.getToolCalls());
                    }*/
                    if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                        respuesta.setEsRespuestaFinal(false);

                        // Si es la primera herramienta que llega, inicializamos la lista
                        if (respuesta.getToolCalls() == null) {
                            respuesta.setToolCalls(new java.util.ArrayList<>(message.getToolCalls()));
                        } else {
                            // Si ya teníamos herramientas de un chunk anterior, las acumulamos
                            respuesta.getToolCalls().addAll(message.getToolCalls());
                        }
                    }

                }

                // Control de errores controlados por parte de Ollama
                if (res.getError()!=null) {
                    outputStream.write(("[ERROR] " + res.getError()).getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    respuesta.setEsRespuestaFinal(true);
                    respuestaCompletaOllama.append("[ERROR] " + res.getError());
                }

            }
            respuesta.setRespuestaFinalUsuario(respuestaCompletaOllama.toString());
            return respuesta;
        }
    }

    private String getErrorHttpOllama( HttpResponse<InputStream> respuestaHttpConError) throws IOException {
        StringBuilder resultado = new StringBuilder();
        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(respuestaHttpConError.body(), StandardCharsets.UTF_8))) {
            while ((line = reader.readLine()) != null) {
                resultado.append(line);
            }
        }
        return resultado.toString();
    }

    // Consultamos Alejandria
    private String obtenerContenidoSimilar(String peticionUsuario, String urlAlejandria, String tokenAlejandria, Integer ragMaxItems, Double ragMinScoreLimit) throws IOException, InterruptedException {

        // Petición RAG Alejandria
        AlejandriaRequest peticionAlejandria = new AlejandriaRequest(peticionUsuario, ragMaxItems);
        String jsonBody = new ObjectMapper().writeValueAsString(peticionAlejandria);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlAlejandria))
                .header("Authorization", "Bearer " + tokenAlejandria)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Procesamos respuesta Alejandria
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        if (response.statusCode() == 200) {
            AlejandriaResponse respuestaAlejandria = objectMapper.readValue(response.body(),AlejandriaResponse.class);
            if (respuestaAlejandria.getResultado().equals("ok")) {
                int index = 1;
                StringBuilder respuesta = new StringBuilder();
                for (AlejandriaItem item : respuestaAlejandria.getFragmentos()) {

                    if (item.getScore() <= ragMinScoreLimit.floatValue()) continue; // Mínimo de similitud requerido

                    try {
                        JsonNode nodo = objectMapper.readTree(item.getTexto());
                        String contenido = nodo.get("content").asText();
                        String start = nodo.get("start_line").asText();
                        String end = nodo.get("end_line").asText();

                        respuesta.append(String.format("[Documento %d (Líneas %s-%s)]:\n%s\n\n", index++, start, end, contenido));
                    } catch (Exception e) {
                        LOGGER.warn("No se pudo parsear el JSON del chunk de Alejandría: " +item.getTexto());
                    }
                }

                return respuesta.toString().trim();
            }
            else {
                LOGGER.debug("Respuesta alejandria ko");
                return null;
            }


        } else if (response.statusCode() == 401) {
            LOGGER.warn("Token caducado. Reintentando identificación...");
            this.chatSession.refreshTokenAlejandria();
            return null;
        } else {
            throw new RuntimeException("Error en Alejandría: " + response.statusCode() + " - " + response.body());
        }
    }

}
