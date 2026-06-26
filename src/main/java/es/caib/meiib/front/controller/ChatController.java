package es.caib.meiib.front.controller;

import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.*;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.caib.meiib.service.exception.ServiceException;
import es.caib.meiib.service.interfaces.*;
import es.caib.meiib.service.models.*;
import es.caib.meiib.service.models.agentIA.*;

import org.primefaces.event.SelectEvent;
import org.primefaces.event.SlideEndEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Named
@SessionScoped
public class ChatController implements Serializable  {

    private static final boolean STREAM = true;
    private static final String KEEPALIVE = "1h";

    private String tituloChat = "";
    private List<ChatIA> chatsGuardados;

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatController.class);

    private OllamaChatRequest ollamaChatRequest = null;
    private AgentIA agentIA = null;
    private String tokenAlejandria = null;
    private ChatIA chatIA = null;

    @Inject
    private IChatIAService chatIAServiceBean;

    @Inject
    private IAgentIAService agentIAServiceBean;

    // Métodos callback

    @PostConstruct
    public void init() {

        try {
            this.chatsGuardados = this.chatIAServiceBean.getAllChats();
            this.agentIA = agentIAServiceBean.getDefaultAgentIA();
            this.ollamaChatRequest = initOllamaChatRequest(this.agentIA);
            this.tokenAlejandria = getTokenAlejandria(this.agentIA.getUrlAuthAlejandria(), this.agentIA.getClientIdAlejandria(), this.agentIA.getSecretAlejandria());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // Nueva conversaciñon
    public void newChat() {
        try {
            this.chatIA = null;
            this.tituloChat = "";
            this.chatsGuardados = this.chatIAServiceBean.getAllChats();
            this.agentIA = agentIAServiceBean.getDefaultAgentIA();
            this.ollamaChatRequest = initOllamaChatRequest(this.agentIA);
            this.tokenAlejandria = getTokenAlejandria(this.agentIA.getUrlAuthAlejandria(), this.agentIA.getClientIdAlejandria(), this.agentIA.getSecretAlejandria());
        } catch(Exception e) {
            LOGGER.error("Error inicializando nuevo chat", e);
            FacesContext context = FacesContext.getCurrentInstance();
            context.addMessage("fixedGrowl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", "Error inicializando nuevo chat."));
        }
    }
    // Guardamos conversación
    public void saveChat() {

        String jsonPayload = "";
        try (Jsonb jsonb = JsonbBuilder.create()) {
            jsonPayload = jsonb.toJson(this.ollamaChatRequest);
        } catch (Exception e) {

            LOGGER.error("No se ha podido serializar la coonversación, esta no será guardada.");
            return;
        }

        if (this.chatIA==null) {
            this.chatIA = new ChatIA();
            this.chatIA.setIdChat(UUID.randomUUID());
            this.chatIA.setUsuariCreacio("u97091");
            this.chatIA.setUsuariModificacio("u97091");
            this.chatIA.setTitulo(this.tituloChat);
            this.chatIA.setChat(jsonPayload);
            this.chatIAServiceBean.addChat(this.chatIA);
        }
        else  {
            this.chatIA.setTitulo(this.tituloChat);
            this.chatIA.setChat(jsonPayload);
            this.chatIAServiceBean.updateChat(this.chatIA);
        }
        this.chatsGuardados = this.chatIAServiceBean.getAllChats();

    }

    public void deleteChat(UUID idChat) {
        this.chatIAServiceBean.deleteChat(idChat);
        this.chatsGuardados = this.chatIAServiceBean.getAllChats();
    }

    // Getters & Setters
    public String getTituloChat() { return tituloChat;}
    public void setTituloChat(String tituloChat) { this.tituloChat = tituloChat; }

    public List<ChatIA> getChatsGuardados() { return chatsGuardados; }

    public OllamaChatRequest getOllamaChatRequest() { return ollamaChatRequest; }
    public void setOllamaChatRequest(OllamaChatRequest ollamaChatRequest) { this.ollamaChatRequest = ollamaChatRequest; }

    public AgentIA getAgentIA() { return agentIA; }
    public void setAgentIA(AgentIA agentIA) { this.agentIA = agentIA; }

    public String getTokenAlejandria() { return tokenAlejandria; }

    // Métodos privados
    // Obtenemos token para Alejandria
    private String getTokenAlejandria(String urlAuthAlejandria, String clientIdAlejandria, String secretAlejandria) throws IOException, InterruptedException {
        String formData = "grant_type=client_credentials" +
                "&client_id=" + URLEncoder.encode(clientIdAlejandria, "UTF-8") +
                "&client_secret=" + URLEncoder.encode(secretAlejandria, "UTF-8");

        String tokenEndpoint = urlAuthAlejandria;



        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            OpenIDResponse openIDResponse = objectMapper.readValue(response.body(),OpenIDResponse.class);
            if (openIDResponse!=null) {
                LOGGER.debug("Token alejandria: " + openIDResponse.getAccessToken());
                return openIDResponse.getAccessToken();
            }
            return "";
        } else {
            LOGGER.error("Error obteniendo token de Alejandria");
            FacesContext context = FacesContext.getCurrentInstance();
            context.addMessage("fixedGrowl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", "Error obteniendo el token de Alejandria."));
            return "";
        }
    }

    // Inicializa la conversación por primera vez.
    private OllamaChatRequest initOllamaChatRequest(AgentIA agentIA) {

        OllamaChatRequest ollamaChatRequest =new OllamaChatRequest(agentIA.getModel(),STREAM,KEEPALIVE); // Parámetros básicos
        ollamaChatRequest.setOptions(new OllamaOption(agentIA.getModelTemperature(),agentIA.getNumCtx())); // Opciones
        ollamaChatRequest.setTools(new ToolCalling().obtenerFuncionesAPIDatosAbiertos()); // ToolChain - Lista de operaciones disponibles (datos abiertos)

        OllamaMessage ollamaInitialMessage = new OllamaMessage("system", agentIA.getSystemPrompt()); // System prompt
        ArrayList<OllamaMessage> ollamaMessages = new ArrayList<>(List.of(ollamaInitialMessage));

        ollamaChatRequest.setMessages(ollamaMessages);

        return ollamaChatRequest;
    }

    // Cargamos la conversación guardada en base de datos
    private OllamaChatRequest loadOllamaChatRequest(ChatIA chatIA) {

        try (Jsonb jsonb = JsonbBuilder.create()) {
            return jsonb.fromJson(chatIA.getChat(), OllamaChatRequest.class);
        } catch (Exception e) {
            LOGGER.error("Error al deserializar el payload JSON del chat {}", chatIA.getIdChat(), e);
            FacesContext context = FacesContext.getCurrentInstance();
            context.addMessage("fixedGrowl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", "El historial de este chat está dañado."));
            return null;
        }
    }

    // Dudo de tener esto public, pero durante la conversación se nos puede caducar el token de identificación
    public void refreshTokenAlejandria() {
        try {
            this.tokenAlejandria = getTokenAlejandria(this.agentIA.getUrlAuthAlejandria(), this.agentIA.getClientIdAlejandria(), this.agentIA.getSecretAlejandria());
        } catch (Exception e) {
            LOGGER.error("Error al refrescar el Token Alejandria {}", e);
        }
    }

    public void onChatSelect(SelectEvent<ChatIA> event) {

        try {
            ChatIA lazyChat = (ChatIA) event.getObject();
            if (lazyChat==null) return;

            this.chatIA = this.chatIAServiceBean.getChat(lazyChat.getIdChat()); // Recuperamos la conversación de BBDD
            this.tituloChat = this.chatIA.getTitulo();
            // Regeneramos el contexto
            // Deberíamos mirar el JSON de la conversación y deducir que agente usamos, pero este quizás ya no existe. Por ahora simplificamos y seguimos la conversación con el agente default
            // como ya lo tenemos cargado al inicio de la página no hace falta hacer --> this.agentIA = this.agentIAServiceBean.getDefaultAgentIA(); --> tendríamos que hacer this.agentIA (recupera el mensaje usado de la conversación anterior)
            // No hace falta generar un nuevo token alejandria, ya tenemos el de la carga de la página (mientras no esté caducado, ya nos vale) -> this.tokenAlejandria = getTokenAlejandria(this.agentIA.getUrlAuthAlejandria(), this.agentIA.getClientIdAlejandria(), this.agentIA.getSecretAlejandria());

            this.ollamaChatRequest = this.loadOllamaChatRequest(this.chatIA);


        } catch (Exception e) {
            LOGGER.error("Error en onChatSelect: ", e.toString());
            FacesContext context = FacesContext.getCurrentInstance();
            context.addMessage("fixedGrowl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error en onChatSelect", "Error: " + e.getMessage()));
        }
    }

    public void onTemperaturaChange(SlideEndEvent event) {
        double novaTemperatura = event.getValue();
        this.agentIA.setModelTemperature(novaTemperatura);
        this.ollamaChatRequest.getOptions().setTemperature(this.agentIA.getModelTemperature());
    }
}

