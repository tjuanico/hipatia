package es.caib.meiib.front.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.faces.application.Application;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Locale;

/**
 * Bean per mantenir el locale de l'usuari.
 * @author areus
 */
@Named
@SessionScoped
public class UserLocale implements Serializable {

    private static final long serialVersionUID = -3709390221710580769L;

    private final transient Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Inject
    private FacesContext context;

    /**
     * Locale actual de l'usuari
     */
    private Locale current;

    public Locale getCurrent() {
        return current;
    }

    public void setCurrent(Locale current) {
        this.current = current;
    }

    // Mètodes

    /**
     * Inicialització del locale de l'usuari.
     */
    @PostConstruct
    private void init() {
        log.debug("Inicialitzant locale de l'usuari");
        Application app = context.getApplication();
        current = app.getViewHandler().calculateLocale(context);
        log.debug("current locale {}", current);
    }

    public String reload() {
        // Si estic a l'índex no m'interessa canviar el client. M'interessa començar la navegació a index.xhtml
        // Toni Juanico, 17/10/2024. Problema perquè https://pieib.caib.es crea una nova sessió i no manté el multiidioma.
        String requestUrl = ((HttpServletRequest) context.getExternalContext().getRequest()).getRequestURL().toString();
        log.debug("reload: {}", requestUrl);

        if (requestUrl.indexOf("/meiibfront/index.xhtml") > -1 )
            return "/index.xhtml?faces-redirect=true";

        context.getPartialViewContext().getEvalScripts().add("location.replace(location)");
        return null;


    }
}
