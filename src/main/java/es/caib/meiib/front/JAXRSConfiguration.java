package es.caib.meiib.front;

import io.swagger.annotations.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/rest")
public class JAXRSConfiguration extends Application {
    private static final Logger LOG = LoggerFactory.getLogger(JAXRSConfiguration.class);

    public JAXRSConfiguration() {
        //Les aplicacions JAX-RS necessiten un constructor buid.
    }

    /**
     * Podem introduir tasques a realitzar per la inicialització de l'API.
     */
    @PostConstruct
    private void init() {
        LOG.debug("Iniciant rest en front");
    }
}
