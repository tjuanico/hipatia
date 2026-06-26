package es.caib.meiib.front.controller;

import es.caib.meiib.service.models.agentIA.OllamaTool;
import es.caib.meiib.service.models.agentIA.OllamaToolCall;

import java.util.List;

public class OllamaResponseWrapper {
    private boolean esRespuestaFinal;
    private String respuestaFinalUsuario;
    private List<OllamaToolCall> toolCalls;
    private double tokensPorSegundo =0.0;

    // Constructor
    public OllamaResponseWrapper() {}

    // Getters & Setters
    public boolean isEsRespuestaFinal() {
        return esRespuestaFinal;
    }
    public void setEsRespuestaFinal(boolean esRespuestaFinal) {
        this.esRespuestaFinal = esRespuestaFinal;
    }

    public String getRespuestaFinalUsuario() {
        return respuestaFinalUsuario;
    }
    public void setRespuestaFinalUsuario(String respuestaFinalUsuario) { this.respuestaFinalUsuario = respuestaFinalUsuario; }

    public List<OllamaToolCall> getToolCalls() {
        return toolCalls;
    }
    public void setToolCalls(List<OllamaToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public double getTokensPorSegundo() { return tokensPorSegundo; }
    public void setTokensPorSegundo(double tokensPorSegundo) { this.tokensPorSegundo = tokensPorSegundo; }
}
