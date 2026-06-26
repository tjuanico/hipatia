package es.caib.meiib.front.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Proporciona les opcions d'idioma.
 *
 * @author areus
 */
@Named
@ApplicationScoped
public class ApplicationLocales {

    private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Inject
    private FacesContext context;

    private List<Locale> available;

    public List<Locale> getAvailable() {
        return available;
    }

    /**
     * Dins l'inialització és quan carregam la llista d'idiomes.
     */
    @PostConstruct
    private void init() {
        available = new ArrayList<>();

        // Obtenim el nom del mòdul des del ServletContext
        String moduleName = ((javax.servlet.ServletContext) FacesContext.getCurrentInstance()
                .getExternalContext().getContext()).getContextPath();

        // Afegim el locale per defecte i els suportats
        available.add(context.getApplication().getDefaultLocale());
        context.getApplication().getSupportedLocales()
                .forEachRemaining(available::add);

        log.debug("Mòdul: {} - Locales disponibles: {}", moduleName, available);
    }
}
