package es.caib.meiib.front.controller;


import es.caib.meiib.commons.i18n.I18n;
import es.caib.meiib.service.interfaces.ITextService;
import es.caib.meiib.service.models.Text;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Hashtable;

@Named
@RequestScoped
public class TextController {

    // Private properties
    private ITextService textServiceBean;
    private Hashtable<Integer, Text> textosIdSeccio;
    protected final I18n i18n;
    protected final UserLocale userLocale;

    // Constructor
    @Inject
    public TextController(I18n i18n, UserLocale userLocale, ITextService textServiceBean) {
        this.i18n = i18n;
        this.userLocale = userLocale;
        this.textServiceBean = textServiceBean;
    }

    // Methods
    public void loadTextSeccio(int seccioId) {
        this.textosIdSeccio = textServiceBean.getText(seccioId);
    }

    public String getText(int subseccioId) {
        return this.textosIdSeccio.get(subseccioId).getText(this.userLocale.getCurrent().toLanguageTag());
    }

}

